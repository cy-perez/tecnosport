package co.tecnosport.api.presentation.compartido;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.Reloj;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite de intentos por IP (docs/08-seguridad-legal.md) — el límite por cuenta vive en cada caso
 * de uso ({@code IniciarSesion}, {@code RegistrarUsuario}, {@code SolicitarRecuperacion}, {@code
 * CrearPedido}), que ya recibe el correo en su comando; este filtro no necesita leer el cuerpo de
 * la petición. Se registra solo para rutas concretas ({@code ConfiguracionLimiteIntentos} en {@code
 * bootstrap}), no para todos los POST del contrato.
 *
 * <p>La llave incluye la ruta para que cada endpoint tenga su propio presupuesto — un aluvión
 * contra {@code /auth/registro} no consume el de {@code /auth/sesion} desde la misma IP.
 *
 * <p>La IP real del cliente la resuelve {@link IpDelCliente}, compartida con la constancia de
 * autorización de datos.
 */
public class FiltroLimiteIntentos extends OncePerRequestFilter {

  private final LimitadorDeIntentos limitador;
  private final Reloj reloj;
  private final int maximoIntentos;
  private final Duration ventana;

  public FiltroLimiteIntentos(
      LimitadorDeIntentos limitador, Reloj reloj, int maximoIntentos, Duration ventana) {
    this.limitador = Objects.requireNonNull(limitador);
    this.reloj = Objects.requireNonNull(reloj);
    this.maximoIntentos = maximoIntentos;
    this.ventana = Objects.requireNonNull(ventana);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String clave = "ip:" + request.getRequestURI() + ":" + IpDelCliente.de(request);
    boolean permitido = limitador.permitir(clave, maximoIntentos, ventana, reloj.ahora());
    if (!permitido) {
      responderLimiteExcedido(request, response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private void responderLimiteExcedido(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    // HttpServletResponse no define una constante para 429 (Too Many Requests).
    response.setStatus(429);
    // charset explícito: sin esto, getWriter() usa ISO-8859-1 por defecto (spec de servlets) y
    // las tildes del cuerpo (regla dura #4, mensajes de error incluidos) salen corruptas.
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write(
            "{\"type\":\"https://tecnosport.co/errores/limite-de-intentos-excedido\","
                + "\"title\":\"Límite de intentos excedido\","
                + "\"status\":429,"
                + "\"detail\":\"Demasiados intentos. Intenta de nuevo más tarde.\","
                + "\"instance\":\""
                + request.getRequestURI()
                + "\","
                + "\"codigo\":\"LIMITE_DE_INTENTOS_EXCEDIDO\"}");
  }
}
