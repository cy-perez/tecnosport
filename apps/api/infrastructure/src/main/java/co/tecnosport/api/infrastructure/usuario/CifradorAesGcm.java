package co.tecnosport.api.infrastructure.usuario;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra la clave TOTP de cada segundo factor antes de que llegue a la base (ADR-0096).
 *
 * <p>AES-256 en modo GCM ({@code AES/GCM/NoPadding}, del JDK): IV aleatorio de 12 bytes —el tamaño
 * que recomienda la NIST SP 800-38D— y etiqueta de 128 bits, así que un byte cambiado en la base no
 * se descifra a otra clave: falla. El <b>id del usuario va como dato asociado</b>: no se cifra pero
 * entra en la etiqueta, y una fila copiada de una cuenta a otra tampoco se descifra.
 *
 * <p>Formato guardado: {@code "v1:" + Base64(iv ‖ cifrado)}. El prefijo deja sitio para rotar la
 * llave el día que haga falta, leyendo la versión antes de elegir con qué descifrar.
 *
 * <p>Sin puerto en {@code application}: ningún caso de uso cifra ni descifra nada. Es cómo guarda
 * este adaptador, igual que el SHA-256 de los tokens.
 */
public final class CifradorAesGcm {

  public static final int BYTES_DE_LLAVE = 32;
  private static final String VERSION = "v1:";
  private static final int BYTES_DE_IV = 12;
  private static final int BITS_DE_ETIQUETA = 128;
  private static final SecureRandom ALEATORIO = new SecureRandom();

  private final SecretKeySpec llave;

  public CifradorAesGcm(byte[] llave) {
    Objects.requireNonNull(llave, "La llave no puede ser nula.");
    if (llave.length != BYTES_DE_LLAVE) {
      throw new IllegalArgumentException(
          "La llave del segundo factor tiene que ser de " + BYTES_DE_LLAVE + " bytes.");
    }
    this.llave = new SecretKeySpec(llave.clone(), "AES");
  }

  /** La llave como viene en {@code SEGUNDO_FACTOR_LLAVE}: Base64 estándar de 32 bytes. */
  public static CifradorAesGcm desdeBase64(String llaveEnBase64) {
    Objects.requireNonNull(llaveEnBase64, "La llave no puede ser nula.");
    return new CifradorAesGcm(Base64.getDecoder().decode(llaveEnBase64.trim()));
  }

  public String cifrar(byte[] claro, UUID usuarioId) {
    Objects.requireNonNull(claro, "El texto en claro no puede ser nulo.");
    byte[] iv = new byte[BYTES_DE_IV];
    ALEATORIO.nextBytes(iv);
    try {
      Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
      cifra.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(BITS_DE_ETIQUETA, iv));
      cifra.updateAAD(asociado(usuarioId));
      byte[] cifrado = cifra.doFinal(claro);
      byte[] junto = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
      return VERSION + Base64.getEncoder().encodeToString(junto);
    } catch (GeneralSecurityException imposible) {
      // AES/GCM/NoPadding es obligatorio en toda JVM (javadoc de Cipher).
      throw new IllegalStateException("No se pudo cifrar la clave del segundo factor.", imposible);
    }
  }

  /**
   * @throws IllegalStateException si la fila no se descifra con esta llave y este usuario: otra
   *     llave, un byte cambiado o una fila de otra cuenta. No dice cuál, y no lo necesita: en los
   *     tres casos el factor está perdido y el camino es el procedimiento de restablecimiento.
   */
  public byte[] descifrar(String guardado, UUID usuarioId) {
    Objects.requireNonNull(guardado, "El texto cifrado no puede ser nulo.");
    if (!guardado.startsWith(VERSION)) {
      throw new IllegalStateException("La clave del segundo factor tiene un formato desconocido.");
    }
    try {
      byte[] junto = Base64.getDecoder().decode(guardado.substring(VERSION.length()));
      if (junto.length <= BYTES_DE_IV) {
        throw new IllegalStateException("La clave del segundo factor está incompleta.");
      }
      Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
      cifra.init(
          Cipher.DECRYPT_MODE,
          llave,
          new GCMParameterSpec(BITS_DE_ETIQUETA, Arrays.copyOfRange(junto, 0, BYTES_DE_IV)));
      cifra.updateAAD(asociado(usuarioId));
      return cifra.doFinal(junto, BYTES_DE_IV, junto.length - BYTES_DE_IV);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("La clave del segundo factor no se pudo descifrar.", e);
    }
  }

  private static byte[] asociado(UUID usuarioId) {
    Objects.requireNonNull(usuarioId, "El id del usuario no puede ser nulo.");
    return usuarioId.toString().getBytes(StandardCharsets.UTF_8);
  }
}
