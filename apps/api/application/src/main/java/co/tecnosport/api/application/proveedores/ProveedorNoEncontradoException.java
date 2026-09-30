package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/** No hay un proveedor con ese id. */
public final class ProveedorNoEncontradoException extends RuntimeException {

  public ProveedorNoEncontradoException(UUID id) {
    super("No existe un proveedor con id " + id + ".");
  }
}
