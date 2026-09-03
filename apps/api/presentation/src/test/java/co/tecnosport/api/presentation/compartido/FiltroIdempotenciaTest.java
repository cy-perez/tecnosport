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

  @Test
  void sinCabeceraDejaPasarSinReclamarNada() throws Exception {
    AtomicInteger llamadasACadena = new AtomicInteger();

    filtro.doFilter(
        peticion(null), new MockHttpServletResponse(), cadenaContando(llamadasACadena, 200));

    assertThat(llamadasACadena.get()).isEqualTo(1);
    assertThat(repositorio.buscarCompletada("cualquiera", AHORA)).isEmpty();
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

  @Test
  void unErrorDeNegocioMenorA500TambienSeCachea() throws Exception {
    filtro.doFilter(
        peticion("llave-3"),
        new MockHttpServletResponse(),
        cadenaQueResponde(
            409, "application/problem+json", "{\"codigo\":\"EXISTENCIA_INSUFICIENTE\"}"));

    RespuestaIdempotente cacheada = repositorio.buscarCompletada("llave-3", AHORA).orElseThrow();
    assertThat(cacheada.estadoHttp()).isEqualTo(409);
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
  void unaLlaveYaReclamadaPeroSinCompletarDejaPasarLaPeticion() throws Exception {
    repositorio.reclamar("llave-5", "POST", "/api/v1/pedidos", AHORA);
    AtomicInteger llamadasACadena = new AtomicInteger();

    filtro.doFilter(
        peticion("llave-5"), new MockHttpServletResponse(), cadenaContando(llamadasACadena, 200));

    assertThat(llamadasACadena.get()).isEqualTo(1);
  }
}
