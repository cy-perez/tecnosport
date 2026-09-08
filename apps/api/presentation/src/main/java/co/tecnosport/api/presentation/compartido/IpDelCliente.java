package co.tecnosport.api.presentation.compartido;

import jakarta.servlet.http.HttpServletRequest;

/**
 * La IP real de quien hace la petición. Vivía dentro de {@code FiltroLimiteIntentos}, que fue quien
 * la necesitó primero; ahora también la necesita la constancia de autorización de datos
 * (docs/08-seguridad-legal.md: "se guarda fecha, versión del texto e IP"), y dos copias de esta
 * lógica se habrían separado en la primera corrección.
 *
 * <p>Detrás del balanceador (docs/07-infra-gcp.md: Cloud Load Balancing delante de Cloud Run, con
 * varias instancias) {@code getRemoteAddr()} devuelve la IP del balanceador, no la del cliente. Se
 * lee {@code X-Forwarded-For} —el primer valor de la lista, que es el cliente original— y se cae a
 * {@code getRemoteAddr()} cuando esa cabecera no viene: desarrollo local, sin balanceador delante.
 */
public final class IpDelCliente {

  private static final String CABECERA_ADELANTE = "X-Forwarded-For";

  private IpDelCliente() {}

  public static String de(HttpServletRequest request) {
    String adelante = request.getHeader(CABECERA_ADELANTE);
    if (adelante != null && !adelante.isBlank()) {
      String primero = adelante.split(",")[0].trim();
      // Una cabecera que empieza por coma (", 10.0.0.1") dejaría un valor vacío. Como llave de
      // límite de intentos metería a todo el mundo en el mismo cubo, y como constancia de
      // autorización no diría nada: mejor la del balanceador, que al menos es cierta.
      if (!primero.isEmpty()) {
        return primero;
      }
    }
    return request.getRemoteAddr();
  }
}
