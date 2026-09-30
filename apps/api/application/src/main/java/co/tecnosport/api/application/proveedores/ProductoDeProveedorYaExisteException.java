package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/**
 * Ya hay un producto de este proveedor con la misma huella: el mismo anuncio, al mismo precio. Pasa
 * cuando el proveedor repite la publicación y quedan dos borradores en revisión: aprobar el segundo
 * crearía un duplicado, y el índice único lo impediría con un 500. Se dice antes.
 */
public final class ProductoDeProveedorYaExisteException extends RuntimeException {
  public ProductoDeProveedorYaExisteException(UUID productoId) {
    super(
        "Ya existe un producto de este proveedor con el mismo título y precio ("
            + productoId
            + "). Rechaza este borrador: es el mismo anuncio repetido.");
  }
}
