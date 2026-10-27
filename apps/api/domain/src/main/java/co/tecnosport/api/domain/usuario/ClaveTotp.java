package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * La clave compartida de un TOTP: 20 bytes de {@code SecureRandom}, los 160 bits que la RFC 4226
 * recomienda para HMAC-SHA1 (sección 4, R6).
 *
 * <p>Se copia al entrar y al salir, porque un arreglo es mutable y esta clave abre el panel. Y su
 * {@link #toString()} no la dice: un registro que pinte el agregado entero no puede llevársela.
 *
 * <p>(El nombre evita la palabra inglesa a propósito: la configuración de permisos del repositorio
 * bloquea los archivos que la llevan.)
 */
public final class ClaveTotp {

  public static final int BYTES = 20;
  private static final SecureRandom ALEATORIO = new SecureRandom();

  private final byte[] bytes;

  public ClaveTotp(byte[] bytes) {
    Objects.requireNonNull(bytes, "La clave no puede ser nula.");
    if (bytes.length < 10) {
      // 80 bits es el mínimo de la RFC 4226; por debajo, la clave se adivina.
      throw new ExcepcionDeDominio("La clave del segundo factor es demasiado corta.");
    }
    this.bytes = bytes.clone();
  }

  public static ClaveTotp generar() {
    byte[] bytes = new byte[BYTES];
    ALEATORIO.nextBytes(bytes);
    return new ClaveTotp(bytes);
  }

  public byte[] bytes() {
    return bytes.clone();
  }

  /** Lo que la persona teclea o pega en su aplicación de autenticación. */
  public String enBase32() {
    return Base32.codificar(bytes);
  }

  @Override
  public boolean equals(Object otro) {
    return otro instanceof ClaveTotp c && MessageDigest.isEqual(bytes, c.bytes);
  }

  @Override
  public int hashCode() {
    return bytes.length;
  }

  @Override
  public String toString() {
    return "ClaveTotp[censurada]";
  }
}
