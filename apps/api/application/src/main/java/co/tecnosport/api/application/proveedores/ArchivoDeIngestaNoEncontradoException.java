package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/** No hay un archivo de ingesta con ese id. */
public final class ArchivoDeIngestaNoEncontradoException extends RuntimeException {

  public ArchivoDeIngestaNoEncontradoException(UUID id) {
    super("No existe un archivo de ingesta con id " + id + ".");
  }
}
