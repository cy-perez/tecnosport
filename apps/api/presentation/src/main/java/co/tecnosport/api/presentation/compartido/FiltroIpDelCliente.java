package co.tecnosport.api.presentation.compartido;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resuelve una sola vez, al entrar, la IP real del cliente y la deja en la petición para {@link
 * IpDelCliente} (ADR-0087).
 *
 * <h2>Por qué no basta con leer {@code X-Forwarded-For}</h2>
 *
 * <p>Cada intermediario de Google <b>agrega</b> al final la IP de quien se le conectó y conserva lo
 * que venía antes, y lo que venía antes lo escribe cualquiera. Lo único de fiar es la cola de la
 * lista, y cuánta cola depende del camino, que hoy son dos:
 *
 * <ul>
 *   <li>directo a {@code tecnosport-api-….run.app}: {@code [inventado], IP real} — la API está
 *       abierta a internet porque los webhooks y Cloud Tasks llegan así;
 *   <li>por el proxy de {@code /api} de la web: {@code IP de salida de la web}, porque el proxy ya
 *       no reenvía la cabecera del visitante. Esa IP la comparten todos los visitantes, así que la
 *       real tiene que venir aparte.
 * </ul>
 *
 * <p>La regla, en orden:
 *
 * <ol>
 *   <li>Si llega {@code X-Clave-Proxy} con la clave configurada, se cree {@code X-Ip-Cliente}: la
 *       escribió el proxy de la web, que la sacó de la cola de su propio frente.
 *   <li>Si no, el valor que está {@code saltosConfiables} posiciones antes del final de {@code
 *       X-Forwarded-For}: 1 mientras la API se publique directo, 2 el día que haya un balanceador
 *       delante y la API solo acepte tráfico de él.
 *   <li>Si no hay cabecera, o es más corta que eso, {@code getRemoteAddr()}: desarrollo local.
 * </ol>
 *
 * <p>Sin clave configurada el paso 1 no existe: {@code X-Ip-Cliente} se ignora venga como venga. Es
 * el valor por omisión a propósito, porque el entrenamiento CDS del Dockerfile arranca la API sin
 * secretos y porque lo inseguro no debe ser lo que pasa cuando falta una variable.
 */
public class FiltroIpDelCliente extends OncePerRequestFilter {

  static final String CABECERA_ADELANTE = "X-Forwarded-For";
  static final String CABECERA_IP = "X-Ip-Cliente";
  static final String CABECERA_CLAVE = "X-Clave-Proxy";

  /**
   * Una IPv4 o una IPv6, y nada más largo que la más larga posible. No valida la dirección: impide
   * que la constancia de autorización guarde texto arbitrario o que una llave del limitador mida un
   * kilobyte.
   */
  private static final Pattern FORMA_DE_IP = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

  private final byte[] claveProxy;
  private final int saltosConfiables;

  /**
   * @param claveProxy la que comparte con el proxy de la web; vacía o nula si no hay proxy de fiar
   * @param saltosConfiables cuántos valores del final de {@code X-Forwarded-For} agregó la propia
   *     infraestructura; el primero de ellos es el cliente
   */
  public FiltroIpDelCliente(String claveProxy, int saltosConfiables) {
    if (saltosConfiables < 1) {
      throw new IllegalArgumentException("saltosConfiables debe ser al menos 1.");
    }
    this.claveProxy =
        claveProxy == null || claveProxy.isBlank()
            ? null
            : claveProxy.getBytes(StandardCharsets.UTF_8);
    this.saltosConfiables = saltosConfiables;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    request.setAttribute(IpDelCliente.ATRIBUTO, resolver(request));
    filterChain.doFilter(request, response);
  }

  String resolver(HttpServletRequest request) {
    if (vieneDelProxy(request)) {
      String delProxy = conFormaDeIp(request.getHeader(CABECERA_IP));
      if (delProxy != null) {
        return delProxy;
      }
    }
    String adelante = adelanteCompleto(request);
    if (adelante != null) {
      String[] saltos = adelante.split(",", -1);
      int posicion = saltos.length - saltosConfiables;
      if (posicion >= 0) {
        String delFrente = conFormaDeIp(saltos[posicion]);
        if (delFrente != null) {
          return delFrente;
        }
      }
    }
    return request.getRemoteAddr();
  }

  /**
   * Todas las líneas de {@code X-Forwarded-For} unidas en una lista, y no solo la primera, que es
   * lo que devuelve {@code getHeader}. Con dos líneas, la primera puede ser entera del cliente, y
   * su último valor también: leer solo esa reabría el hueco sin depender de lo que el frente de
   * Google haga con las cabeceras repetidas. Unidas, el final de la lista sigue siendo lo que
   * agregó la infraestructura. Lo levantó la revisión de seguridad.
   */
  private static String adelanteCompleto(HttpServletRequest request) {
    java.util.List<String> lineas =
        java.util.Collections.list(request.getHeaders(CABECERA_ADELANTE));
    return lineas.isEmpty() ? null : String.join(",", lineas);
  }

  /** En tiempo constante: compararla con {@code equals} dejaría adivinarla letra por letra. */
  private boolean vieneDelProxy(HttpServletRequest request) {
    String recibida = request.getHeader(CABECERA_CLAVE);
    return claveProxy != null
        && recibida != null
        && MessageDigest.isEqual(claveProxy, recibida.getBytes(StandardCharsets.UTF_8));
  }

  private static String conFormaDeIp(String crudo) {
    if (crudo == null) {
      return null;
    }
    String valor = crudo.trim();
    return FORMA_DE_IP.matcher(valor).matches() ? valor : null;
  }
}
