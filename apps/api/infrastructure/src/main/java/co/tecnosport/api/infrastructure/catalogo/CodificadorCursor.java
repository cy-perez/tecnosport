package co.tecnosport.api.infrastructure.catalogo;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Codifica el cursor opaco de paginación como {@code claveDeOrden|id} en Base64. {@code
 * claveDeOrden} es el valor de la columna de orden del último elemento visto (precio, fecha o
 * similitud, según {@code OrdenProductos}) — quien llama no necesita saber qué hay adentro.
 */
public final class CodificadorCursor {

  private CodificadorCursor() {}

  public static String codificar(String claveOrden, UUID id) {
    String crudo = claveOrden + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(crudo.getBytes(StandardCharsets.UTF_8));
  }

  public static Decodificado decodificar(String cursor) {
    try {
      String crudo = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int separador = crudo.lastIndexOf('|');
      if (separador < 0) {
        throw new CursorInvalidoException();
      }
      UUID id = UUID.fromString(crudo.substring(separador + 1));
      return new Decodificado(crudo.substring(0, separador), id);
    } catch (IllegalArgumentException excepcionOriginal) {
      throw new CursorInvalidoException();
    }
  }

  public record Decodificado(String claveOrden, UUID id) {}

  // Extiende IllegalArgumentException (no RuntimeException) a propósito:
  // presentation no puede depender de infrastructure para capturarla por su
  // nombre, pero sí puede capturar IllegalArgumentException genéricamente.
  public static final class CursorInvalidoException extends IllegalArgumentException {
    public CursorInvalidoException() {
      super("Cursor de paginación inválido.");
    }
  }
}
