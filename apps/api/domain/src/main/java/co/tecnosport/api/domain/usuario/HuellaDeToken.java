package co.tecnosport.api.domain.usuario;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que se guarda de un token de sesión, de recuperación o de verificación: su SHA-256 en
 * hexadecimal, nunca el token. Hasta el 27 de octubre de 2026 el token era la llave primaria tal
 * cual, así que una copia de la base —un respaldo, una lectura por inyección, una consola con
 * permisos de más— entregaba sesiones de 30 días y enlaces de cambio de clave listos para usar.
 *
 * <p>Sin sal ni costo, y es correcto aquí: el token son 74 bits de {@code SecureRandom}, no una
 * clave que una persona elige, así que no hay diccionario contra el que probar. Lo que hace falta
 * es que la búsqueda sea directa por la huella, y una sal lo impediría.
 *
 * <p>El texto que se resume es {@link UUID#toString()} en UTF-8 —minúsculas, con guiones—, que es
 * lo mismo que {@code id::text} en PostgreSQL. La migración {@code V140} convierte con esa igualdad
 * las filas que ya existían, y por eso las sesiones vivas no se cierran.
 */
public final class HuellaDeToken {

  private HuellaDeToken() {}

  public static String de(UUID token) {
    Objects.requireNonNull(token, "El token no puede ser nulo.");
    return de(token.toString());
  }

  /**
   * La misma huella de un texto cualquiera. La usan los códigos de respaldo del segundo factor
   * (ADR-0096), que son 80 bits de {@code SecureRandom} y por la misma razón no necesitan sal: se
   * buscan por la huella.
   */
  public static String de(String texto) {
    Objects.requireNonNull(texto, "El texto no puede ser nulo.");
    try {
      byte[] resumen =
          MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(resumen);
    } catch (NoSuchAlgorithmException imposible) {
      // Toda JVM tiene que traer SHA-256 (javadoc de MessageDigest).
      throw new IllegalStateException(imposible);
    }
  }
}
