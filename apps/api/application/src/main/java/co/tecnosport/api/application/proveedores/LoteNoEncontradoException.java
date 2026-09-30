package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/** No hay un lote con ese id. */
public final class LoteNoEncontradoException extends RuntimeException {

  public LoteNoEncontradoException(UUID id) {
    super("No existe un lote de ingesta con id " + id + ".");
  }
}
