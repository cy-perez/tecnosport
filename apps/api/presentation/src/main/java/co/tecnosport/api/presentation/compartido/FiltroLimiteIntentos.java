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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

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
 * <p><b>La ruta de la llave es la canónica, nunca {@code getRequestURI()}.</b> La URI cruda llega
 * sin decodificar: el contenedor sí decodifica y normaliza antes de decidir qué filtro aplica, así
 * que {@code /api/v1/auth/%73esion}, {@code /api/v1/auth/sesion;x=1} o {@code
 * /api/v1/auth/./sesion} pasaban por este filtro como {@code /sesion} y caían cada una en un cubo
 * nuevo: cambiar un carácter por su escape bastaba para estrenar presupuesto. La llave usa la ruta
 * tal como la emparejó el contenedor —decodificada, sin parámetros de ruta, sin barras dobles ni
 * segmentos de punto— y conserva así un presupuesto por endpoint, que es lo que el diseño busca.
 *
 * <p>La IP real del cliente la resuelve {@link IpDelCliente}, compartida con la constancia de
 * autorización de datos.
 */
public class FiltroLimiteIntentos extends OncePerRequestFilter {

  private final LimitadorDeIntentos limitador;
  private final Reloj reloj;
  private final int maximoIntentos;
  private final Duration ventana;
  private final String grupo;

  public FiltroLimiteIntentos(
      LimitadorDeIntentos limitador, Reloj reloj, int maximoIntentos, Duration ventana) {
    this(limitador, reloj, maximoIntentos, ventana, null);
  }

  /**
   * Con {@code grupo}, todas las rutas del filtro comparten un solo presupuesto por IP con ese
   * nombre, en vez de uno por URI. Hace falta para una ruta con un parámetro en el camino ({@code
   * /pagos/intentos/{referencia}}): contada por URI, cada referencia estrenaba presupuesto, y
   * recorrer referencias —que es justo el abuso— no tenía techo.
   */
  public FiltroLimiteIntentos(
      LimitadorDeIntentos limitador,
      Reloj reloj,
      int maximoIntentos,
      Duration ventana,
      String grupo) {
    this.limitador = Objects.requireNonNull(limitador);
    this.reloj = Objects.requireNonNull(reloj);
    this.maximoIntentos = maximoIntentos;
    this.ventana = Objects.requireNonNull(ventana);
    this.grupo = grupo;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // Por cubo y no por dirección: una IPv6 se cuenta por su /64, o cada dirección del mismo rango
    // estrenaría presupuesto (CuboDeIp, ADR-0087).
    String ruta = grupo != null ? grupo : rutaCanonica(request);
    String clave = "ip:" + ruta + ":" + CuboDeIp.de(IpDelCliente.de(request));
    boolean permitido = limitador.permitir(clave, maximoIntentos, ventana, reloj.ahora());
    if (!permitido) {
      responderLimiteExcedido(request, response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  /**
   * La ruta dentro de la aplicación, decodificada y sin {@code ;parametros} ({@link
   * UrlPathHelper#defaultInstance}, que además colapsa las barras dobles), con los segmentos {@code
   * .} y {@code ..} resueltos ({@link StringUtils#cleanPath}). Es la forma contra la que el
   * contenedor emparejó el patrón exacto que registró este filtro.
   */
  static String rutaCanonica(HttpServletRequest request) {
    return StringUtils.cleanPath(UrlPathHelper.defaultInstance.getPathWithinApplication(request));
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
