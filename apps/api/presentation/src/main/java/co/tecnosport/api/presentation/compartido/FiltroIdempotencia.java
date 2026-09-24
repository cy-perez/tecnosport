package co.tecnosport.api.presentation.compartido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.RepositorioIdempotencia;
import co.tecnosport.api.application.compartido.RespuestaIdempotente;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Idempotencia por cabecera {@code Idempotency-Key} (docs/03-api.md). Se registra solo para las
 * rutas que mueven dinero o inventario ({@code ConfiguracionIdempotencia} en {@code bootstrap}), no
 * para todos los POST del contrato.
 *
 * <p>Sin cabecera, no hace nada: la llave es aceptada, no exigida. Con cabecera: si ya hay una
 * respuesta completada y vigente, la repite sin tocar el controlador; si no, reclama la llave
 * (comprometida de inmediato, ver {@code RepositorioIdempotencia}) y deja pasar la petición,
 * guardando la respuesta al terminar — salvo que sea un 500, que libera la llave en vez de cachear
 * un error de infraestructura por 24 horas.
 */
public class FiltroIdempotencia extends OncePerRequestFilter {

  private static final String CABECERA = "Idempotency-Key";

  private final RepositorioIdempotencia repositorio;
  private final Reloj reloj;

  public FiltroIdempotencia(RepositorioIdempotencia repositorio, Reloj reloj) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.reloj = Objects.requireNonNull(reloj);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String llave = request.getHeader(CABECERA);
    if (llave == null || llave.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }

    Optional<RespuestaIdempotente> completada = repositorio.buscarCompletada(llave, reloj.ahora());
    if (completada.isPresent()) {
      repetir(response, completada.get());
      return;
    }

    boolean reclamada =
        repositorio.reclamar(llave, request.getMethod(), request.getRequestURI(), reloj.ahora());
    if (!reclamada) {
      // Ya reclamada por otra petición con la misma llave, y todavía sin completar: la gemela
      // sigue en vuelo.
      //
      // **Antes se dejaba pasar, y con eso la llave no protegía de nada en el caso que importa.**
      // Dos peticiones simultáneas con la misma llave —el comprador que pulsa dos veces, el
      // reintento del navegador— se ejecutaban las dos: dos pedidos, dos reservas del mismo
      // inventario, o dos solicitudes de crédito a nombre de una persona. Lo que salvaba el caso
      // de Sistecrédito era el `unique` de `pago.referencia`, no esto.
      //
      // 409 y no espera: construir un bloqueo distribuido para que la segunda espere a la primera
      // es mucha maquinaria para un caso que se resuelve reintentando. Quien reciba esto vuelve a
      // mandar la misma llave dentro de un momento y se encuentra la respuesta guardada, que es
      // exactamente lo que la idempotencia promete.
      enConflicto(response);
      return;
    }

    ContentCachingResponseWrapper envoltorio = new ContentCachingResponseWrapper(response);
    try {
      filterChain.doFilter(request, envoltorio);
      if (envoltorio.getStatus() < 500) {
        repositorio.completar(llave, respuestaDe(envoltorio), reloj.ahora());
      } else {
        repositorio.liberar(llave);
      }
    } catch (RuntimeException | ServletException | IOException e) {
      repositorio.liberar(llave);
      throw e;
    } finally {
      envoltorio.copyBodyToResponse();
    }
  }

  private RespuestaIdempotente respuestaDe(ContentCachingResponseWrapper envoltorio) {
    String cuerpo = new String(envoltorio.getContentAsByteArray(), StandardCharsets.UTF_8);
    return new RespuestaIdempotente(envoltorio.getStatus(), envoltorio.getContentType(), cuerpo);
  }

  /**
   * El cuerpo se arma a mano porque este filtro corre <b>antes</b> del {@code DispatcherServlet},
   * así que el {@code @RestControllerAdvice} no lo ve. Se respeta el mismo formato {@code
   * application/problem+json} que el resto de los errores, con su {@code codigo}, para que el
   * frontend lo traduzca como cualquier otro.
   *
   * <p>El charset se fija <b>antes</b> de pedir el {@code Writer}: sin eso la spec de servlets
   * escribe en ISO-8859-1 y las tildes salen corrompidas, en silencio. Ya pasó en este proyecto, en
   * {@code FiltroLimiteIntentos} (apps/api/CLAUDE.md).
   */
  private void enConflicto(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_CONFLICT);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            "{\"type\":\"https://tecnosport.co/errores/peticion-en-curso\","
                + "\"title\":\"Petición en curso\","
                + "\"status\":409,"
                + "\"detail\":\"Esa misma petición todavía se está procesando."
                + " Vuelve a intentarlo en un momento.\","
                + "\"codigo\":\"PETICION_EN_CURSO\"}");
  }

  private void repetir(HttpServletResponse response, RespuestaIdempotente respuesta)
      throws IOException {
    response.setStatus(respuesta.estadoHttp());
    if (respuesta.tipoContenido() != null) {
      response.setContentType(respuesta.tipoContenido());
    }
    response.getWriter().write(respuesta.cuerpo());
  }
}
