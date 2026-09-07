package co.tecnosport.api.domain.compartido;

import java.util.regex.Pattern;

/**
 * El SHA-256 del contenido de un archivo, en hexadecimal (docs/02-modelo-datos.md: "hash del
 * contenido para detectar recargas duplicadas del mismo archivo").
 *
 * <p>Lo calcula quien tiene los bytes. En una subida directa a Cloud Storage esos bytes nunca pasan
 * por el servidor, así que el hash llega del cliente y el servidor no puede verificarlo sin
 * descargar el archivo — la misma limitación que ya aceptó ADR-0016. Lo que sí se verifica es el
 * formato: un valor que no sea un SHA-256 bien formado no entra.
 *
 * <p>Normalizado a minúsculas, para que el mismo contenido produzca siempre el mismo valor y la
 * comparación por igualdad sirva de algo.
 */
public record HashContenido(String valor) {

  /** 64 caracteres hexadecimales: 256 bits. */
  private static final Pattern FORMATO = Pattern.compile("^[0-9a-f]{64}$");

  public HashContenido {
    if (valor == null || valor.isBlank()) {
      throw new HashContenidoInvalidoException("El hash del contenido no puede estar vacío.");
    }
    valor = valor.trim().toLowerCase();
    if (!FORMATO.matcher(valor).matches()) {
      throw new HashContenidoInvalidoException(
          "El hash del contenido debe ser un SHA-256 en hexadecimal, de 64 caracteres.");
    }
  }
}
