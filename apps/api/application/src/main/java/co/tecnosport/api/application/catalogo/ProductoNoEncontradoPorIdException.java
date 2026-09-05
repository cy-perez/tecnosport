package co.tecnosport.api.application.catalogo;

import java.util.UUID;

/**
 * Distinta de {@link ProductoNoEncontradoException} (que mezcla "no existe" con "existe pero está
 * en borrador" a propósito para el visitante público): el panel admin ve todos los estados, así que
 * aquí un 404 significa que el producto genuinamente no existe.
 */
public final class ProductoNoEncontradoPorIdException extends RuntimeException {

  public ProductoNoEncontradoPorIdException(UUID id) {
    super("No existe un producto con id '" + id + "'.");
  }
}
