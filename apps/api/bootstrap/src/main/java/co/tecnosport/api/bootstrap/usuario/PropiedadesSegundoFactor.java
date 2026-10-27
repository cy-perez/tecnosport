package co.tecnosport.api.bootstrap.usuario;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El segundo factor del panel (ADR-0096). {@code SEGUNDO_FACTOR_LLAVE} y el resto de variables de
 * docs/07-infra-gcp.md.
 *
 * <p>La llave es AES-256: 32 bytes en Base64 estándar. No tiene valor por omisión fuera de {@code
 * local} y aquí se valida entera, porque una llave mal cargada no falla al arrancar sino al primer
 * inicio de sesión de un administrador, y entonces ya cifró con ella lo que activó.
 *
 * <p>Los topes del segundo paso del inicio de sesión: {@code intentosPorDesafio} por cada desafío
 * —agotado, el desafío muere—, {@code intentosCuentaIp} por cuenta y origen y {@code
 * intentosCuenta} por cuenta sola, los dos en {@code minutosVentana}. Por cuenta sola tiene que ser
 * al menos el de cuenta e IP: si no, quien conozca el correo deja fuera al titular desde otra IP,
 * el mismo razonamiento de {@code IniciarSesion}.
 */
@ConfigurationProperties(prefix = "tecnosport.segundo-factor")
public record PropiedadesSegundoFactor(
    String llave,
    boolean adminObligatorio,
    int minutosDesafio,
    int intentosPorDesafio,
    int intentosCuentaIp,
    int intentosCuenta,
    int minutosVentana,
    int minutosActivacionPendiente,
    String emisor) {

  public PropiedadesSegundoFactor {
    // `${` es un marcador que el Binder no resolvió y entregó como texto: ver PropiedadesJwt.
    if (llave == null || llave.isBlank() || llave.contains("${")) {
      throw new IllegalStateException(
          "tecnosport.segundo-factor.llave no está configurada: falta la variable de entorno"
              + " SEGUNDO_FACTOR_LLAVE.");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(llave.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "SEGUNDO_FACTOR_LLAVE no es Base64 estándar (¿base64url?).", e);
    }
    if (bytes.length != 32) {
      throw new IllegalStateException(
          "SEGUNDO_FACTOR_LLAVE tiene que ser de 32 bytes (AES-256) y es de " + bytes.length + ".");
    }
    positivo(minutosDesafio, "minutos-desafio");
    positivo(intentosPorDesafio, "intentos-por-desafio");
    positivo(intentosCuentaIp, "intentos-cuenta-ip");
    positivo(intentosCuenta, "intentos-cuenta");
    positivo(minutosVentana, "minutos-ventana");
    positivo(minutosActivacionPendiente, "minutos-activacion-pendiente");
    if (intentosCuenta < intentosCuentaIp) {
      throw new IllegalStateException(
          "tecnosport.segundo-factor.intentos-cuenta no puede ser menor que intentos-cuenta-ip:"
              + " cerraría la cuenta antes que al origen que falla.");
    }
    if (emisor == null || emisor.isBlank()) {
      throw new IllegalStateException("tecnosport.segundo-factor.emisor no puede estar vacío.");
    }
  }

  public byte[] llaveEnBytes() {
    return Base64.getDecoder().decode(llave.trim());
  }

  private static void positivo(int valor, String nombre) {
    if (valor <= 0) {
      throw new IllegalStateException(
          "tecnosport.segundo-factor." + nombre + " debe ser mayor que cero.");
    }
  }
}
