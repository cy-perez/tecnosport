package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Objects;

/**
 * Base32 de la RFC 4648 (sección 6), sin relleno. Es el alfabeto con que las aplicaciones de
 * autenticación leen la clave de un TOTP —letras y dígitos del 2 al 7, sin el 0 ni el 1 que se
 * confunden con la O y la I— y con el que se escriben los códigos de respaldo (ADR-0096).
 *
 * <p>Aquí y no en una librería: son cuarenta líneas, y cada dependencia nueva es deuda.
 *
 * <p>Al leer es tolerante con lo que una persona teclea o pega: ignora mayúsculas, espacios y
 * guiones, y también el relleno {@code =} si alguien lo trae. Al escribir no lo es: siempre
 * mayúsculas y sin relleno, que es lo que esperan Google Authenticator, 1Password y compañía.
 */
public final class Base32 {

  static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  private Base32() {}

  public static String codificar(byte[] datos) {
    Objects.requireNonNull(datos, "Los datos no pueden ser nulos.");
    StringBuilder salida = new StringBuilder((datos.length * 8 + 4) / 5);
    int acumulado = 0;
    int bits = 0;
    for (byte b : datos) {
      acumulado = (acumulado << 8) | (b & 0xFF);
      bits += 8;
      while (bits >= 5) {
        salida.append(ALFABETO.charAt((acumulado >>> (bits - 5)) & 0x1F));
        bits -= 5;
      }
    }
    if (bits > 0) {
      salida.append(ALFABETO.charAt((acumulado << (5 - bits)) & 0x1F));
    }
    return salida.toString();
  }

  public static byte[] decodificar(String texto) {
    String limpio = normalizar(texto);
    ByteArrayOutputStream salida = new ByteArrayOutputStream(limpio.length() * 5 / 8);
    int acumulado = 0;
    int bits = 0;
    for (int i = 0; i < limpio.length(); i++) {
      int valor = ALFABETO.indexOf(limpio.charAt(i));
      if (valor < 0) {
        throw new ExcepcionDeDominio("El texto no es Base32.");
      }
      acumulado = (acumulado << 5) | valor;
      bits += 5;
      if (bits >= 8) {
        salida.write((acumulado >>> (bits - 8)) & 0xFF);
        bits -= 8;
      }
    }
    return salida.toByteArray();
  }

  /** Mayúsculas, sin espacios, guiones ni relleno. No valida el alfabeto. */
  static String normalizar(String texto) {
    Objects.requireNonNull(texto, "El texto no puede ser nulo.");
    return texto.replaceAll("[\\s=-]", "").toUpperCase(Locale.ROOT);
  }

  static boolean esDelAlfabeto(String normalizado) {
    for (int i = 0; i < normalizado.length(); i++) {
      if (ALFABETO.indexOf(normalizado.charAt(i)) < 0) {
        return false;
      }
    }
    return true;
  }
}
