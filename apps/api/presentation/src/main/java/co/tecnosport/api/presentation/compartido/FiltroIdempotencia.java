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
      // Ya reclamada por otra petición con la misma llave — no construimos espera ni bloqueo
      // distribuido para esta carrera (ver el plan de este paso). Se deja pasar.
      filterChain.doFilter(request, response);
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

  private void repetir(HttpServletResponse response, RespuestaIdempotente respuesta)
      throws IOException {
    response.setStatus(respuesta.estadoHttp());
    if (respuesta.tipoContenido() != null) {
      response.setContentType(respuesta.tipoContenido());
    }
    response.getWriter().write(respuesta.cuerpo());
  }
}
