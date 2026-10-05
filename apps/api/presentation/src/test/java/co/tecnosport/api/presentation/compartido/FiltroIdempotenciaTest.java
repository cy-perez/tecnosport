package co.tecnosport.api.presentation.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.RespuestaIdempotente;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FiltroIdempotenciaTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");
  private static final Reloj RELOJ_FIJO = () -> AHORA;

  private final RepositorioIdempotenciaFalso repositorio = new RepositorioIdempotenciaFalso();
  private final FiltroIdempotencia filtro = new FiltroIdempotencia(repositorio, RELOJ_FIJO);

  private MockHttpServletRequest peticion(String llave) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/pedidos");
    if (llave != null) {
      request.addHeader("Idempotency-Key", llave);
    }
    return request;
  }

  private FilterChain cadenaQueResponde(int estado, String tipoContenido, String cuerpo) {
    return (req, res) -> {
      HttpServletResponse http = (HttpServletResponse) res;
      http.setStatus(estado);
      if (tipoContenido != null) {
        http.setContentType(tipoContenido);
      }
      if (cuerpo != null) {
        http.getWriter().write(cuerpo);
      }
    };
  }

  private FilterChain cadenaContando(AtomicInteger contador, int estado) {
    return (req, res) -> {
      contador.incrementAndGet();
      ((HttpServletResponse) res).setStatus(estado);
    };
  }

  /**
   * Sin llave no pasa: era opcional, y un cliente que no la mandara no tenía ninguna protección
   * contra el doble envío en las rutas que mueven dinero.
   */
  @Test
  void sinCabeceraSeRechazaConUn400SinLlegarAlControlador() throws Exception {
    AtomicInteger llamadasACadena = new AtomicInteger();
    MockHttpServletResponse respuesta = new MockHttpServletResponse();

    filtro.doFilter(peticion(null), respuesta, cadenaContando(llamadasACadena, 200));

    assertThat(llamadasACadena.get()).isZero();
    assertThat(respuesta.getStatus()).isEqualTo(400);
    assertThat(respuesta.getContentAsString()).contains("LLAVE_DE_IDEMPOTENCIA_REQUERIDA");
  }

  @Test
  void primeraLlamadaEjecutaLaCadenaYCachaLaRespuestaExitosa() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filtro.doFilter(
        peticion("llave-1"),
        response,
        cadenaQueResponde(201, "application/json", "{\"id\":\"abc\"}"));

    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"abc\"}");
    RespuestaIdempotente cacheada = repositorio.buscarCompletada("llave-1", AHORA).orElseThrow();
    assertThat(cacheada.estadoHttp()).isEqualTo(201);
    assertThat(cacheada.cuerpo()).isEqualTo("{\"id\":\"abc\"}");
  }

  @Test
  void unaLlaveYaCompletadaRepiteLaRespuestaSinLlamarLaCadena() throws Exception {
    AtomicInteger llamadasACadena = new AtomicInteger();
    FilterChain cadenaConConteo =
        (req, res) -> {
          llamadasACadena.incrementAndGet();
          cadenaQueResponde(200, "application/json", "{\"id\":\"primero\"}").doFilter(req, res);
        };
    filtro.doFilter(peticion("llave-2"), new MockHttpServletResponse(), cadenaConConteo);

    MockHttpServletResponse segundaRespuesta = new MockHttpServletResponse();
    filtro.doFilter(peticion("llave-2"), segundaRespuesta, cadenaContando(llamadasACadena, 200));

    assertThat(llamadasACadena.get()).isEqualTo(1);
    assertThat(segundaRespuesta.getStatus()).isEqualTo(200);
    assertThat(segundaRespuesta.getContentAsString()).isEqualTo("{\"id\":\"primero\"}");
  }

  /**
   * Un 4xx no deja efecto —la transacción revirtió—, y el frontend reutiliza la llave hasta que la
   * creación sale bien. Cachearlo devolvía el mismo error 24 horas a quien ya lo había corregido.
   */
  @Test
  void unErrorDeNegocioNoSeCacheaYElReintentoCorregidoEntra() throws Exception {
    filtro.doFilter(
        peticion("llave-3"),
        new MockHttpServletResponse(),
        cadenaQueResponde(422, "application/problem+json", "{\"codigo\":\"TELEFONO_INVALIDO\"}"));

    assertThat(repositorio.buscarCompletada("llave-3", AHORA)).isEmpty();
    MockHttpServletResponse corregida = new MockHttpServletResponse();
    filtro.doFilter(
        peticion("llave-3"), corregida, cadenaQueResponde(201, "application/json", "{}"));
    assertThat(corregida.getStatus()).isEqualTo(201);
  }

  @Test
  void unError500NoSeCacheaYLiberaLaLlave() throws Exception {
    filtro.doFilter(
        peticion("llave-4"), new MockHttpServletResponse(), cadenaQueResponde(500, null, null));

    assertThat(repositorio.buscarCompletada("llave-4", AHORA)).isEmpty();
    // Liberada de verdad: se puede volver a reclamar sin que reclamar() devuelva false.
    assertThat(repositorio.reclamar("llave-4", "POST", "/api/v1/pedidos", AHORA)).isTrue();
  }

  @Test
  void unaLlaveYaReclamadaPeroSinCompletarSeRechazaConUn409() throws Exception {
    repositorio.reclamar("llave-5", "POST", "/api/v1/pedidos", AHORA);
    AtomicInteger llamadasACadena = new AtomicInteger();
    MockHttpServletResponse respuesta = new MockHttpServletResponse();

    filtro.doFilter(peticion("llave-5"), respuesta, cadenaContando(llamadasACadena, 200));

    // Lo que importa: la peticion **no llega al controlador**. Antes si, y por eso la llave no
    // protegia del caso que la justifica: dos peticiones simultaneas con la misma llave se
    // ejecutaban las dos, o sea dos pedidos con dos reservas del mismo inventario, o dos
    // solicitudes de credito a nombre de una persona.
    assertThat(llamadasACadena.get()).isZero();
    assertThat(respuesta.getStatus()).isEqualTo(409);
    assertThat(respuesta.getContentType()).startsWith("application/problem+json");
    assertThat(respuesta.getContentAsString()).contains("PETICION_EN_CURSO");
    // Con tilde y bien codificada: este filtro corre antes del DispatcherServlet y arma el cuerpo a
    // mano, que es donde este proyecto ya se comio las tildes una vez.
    assertThat(respuesta.getContentAsString()).contains("Petición en curso");
  }
}
