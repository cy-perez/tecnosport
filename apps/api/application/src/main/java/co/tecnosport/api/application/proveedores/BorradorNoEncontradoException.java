package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/** No hay un borrador con ese id. */
public final class BorradorNoEncontradoException extends RuntimeException {

  public BorradorNoEncontradoException(UUID id) {
    super("No existe un borrador con id " + id + ".");
  }
}
