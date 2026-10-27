package co.tecnosport.api.domain.usuario;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * El código de seis dígitos que cambia cada treinta segundos: TOTP de la RFC 6238 sobre el HOTP de
 * la RFC 4226, con HMAC-SHA1. Son los parámetros por omisión de toda aplicación de autenticación, y
 * los únicos que todas entienden: SHA-256 o un periodo distinto fallan en silencio en varias.
 *
 * <p>Vive en el dominio porque es una regla, no un adaptador: qué código vale en qué instante. Usa
 * {@code javax.crypto.Mac}, del JDK, y es la única razón por la que {@code ArquitecturaTest} deja
 * {@code javax.crypto} en esta capa y en ninguna otra de las puras.
 */
public final class Totp {

  public static final int DIGITOS = 6;
  public static final int PERIODO_SEGUNDOS = 30;
  private static final int MODULO = 1_000_000;

  private Totp() {}

  /** El número de periodo de treinta segundos desde la época Unix: la {@code T} de la RFC 6238. */
  public static long pasoDe(Instant instante) {
    Objects.requireNonNull(instante, "El instante no puede ser nulo.");
    return Math.floorDiv(instante.getEpochSecond(), PERIODO_SEGUNDOS);
  }

  /** El código de seis dígitos de un paso, con ceros a la izquierda. */
  public static String codigoEn(ClaveTotp clave, long paso) {
    Objects.requireNonNull(clave, "La clave no puede ser nula.");
    byte[] resumen = hmacSha1(clave.bytes(), ByteBuffer.allocate(8).putLong(paso).array());
    // Truncado dinámico (RFC 4226, sección 5.3).
    int desplazamiento = resumen[resumen.length - 1] & 0x0F;
    int binario =
        ((resumen[desplazamiento] & 0x7F) << 24)
            | ((resumen[desplazamiento + 1] & 0xFF) << 16)
            | ((resumen[desplazamiento + 2] & 0xFF) << 8)
            | (resumen[desplazamiento + 3] & 0xFF);
    return String.format("%06d", binario % MODULO);
  }

  /**
   * El paso al que corresponde el código, si vale ahora. Acepta el periodo anterior, el actual y el
   * siguiente —un reloj de teléfono desfasado hasta treinta segundos, y el tiempo que tarda una
   * persona en teclear—, y solo los posteriores a {@code ultimoPasoUsado}: un código ya aceptado no
   * se acepta dos veces, ni uno más viejo que él (RFC 6238, sección 5.2).
   *
   * <p>Compara los tres candidatos siempre, y con {@link MessageDigest#isEqual}, para que el tiempo
   * de respuesta no diga cuántos dígitos acertaron ni cuál de los tres pasos era.
   *
   * @param ultimoPasoUsado nulo si nunca se aceptó ninguno.
   */
  public static OptionalLong verificar(
      ClaveTotp clave, String codigo, Instant ahora, Long ultimoPasoUsado) {
    Objects.requireNonNull(clave, "La clave no puede ser nula.");
    if (!esFormatoDeCodigo(codigo)) {
      return OptionalLong.empty();
    }
    byte[] tecleado = codigo.getBytes(StandardCharsets.US_ASCII);
    long actual = pasoDe(ahora);
    long aceptado = -1;
    for (long paso = actual - 1; paso <= actual + 1; paso++) {
      boolean coincide =
          MessageDigest.isEqual(
              tecleado, codigoEn(clave, paso).getBytes(StandardCharsets.US_ASCII));
      boolean nuevo = ultimoPasoUsado == null || paso > ultimoPasoUsado;
      if (coincide && nuevo && aceptado < 0) {
        aceptado = paso;
      }
    }
    return aceptado < 0 ? OptionalLong.empty() : OptionalLong.of(aceptado);
  }

  /** Seis dígitos ASCII, nada más: ni espacios, ni signos, ni dígitos de otros alfabetos. */
  public static boolean esFormatoDeCodigo(String codigo) {
    if (codigo == null || codigo.length() != DIGITOS) {
      return false;
    }
    for (int i = 0; i < codigo.length(); i++) {
      char c = codigo.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  private static byte[] hmacSha1(byte[] llave, byte[] mensaje) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(llave, "HmacSHA1"));
      return mac.doFinal(mensaje);
    } catch (GeneralSecurityException imposible) {
      // Toda JVM tiene que traer HmacSHA1 (javadoc de Mac, "Standard Algorithm Names").
      throw new IllegalStateException(imposible);
    }
  }
}
