package co.tecnosport.api.application.compartido;

import java.util.List;
import java.util.Objects;

/**
 * Página de resultados con cursor. {@code cursorSiguiente} es opaco para quien lo recibe: lo genera
 * y lo interpreta la implementación del puerto que produjo esta página. {@code null} significa que
 * no hay más páginas.
 */
public record ResultadoPaginado<T>(List<T> items, String cursorSiguiente) {

  public ResultadoPaginado {
    Objects.requireNonNull(items, "Los items de un resultado paginado no pueden ser nulos.");
    items = List.copyOf(items);
  }

  public boolean tieneSiguiente() {
    return cursorSiguiente != null;
  }
}
