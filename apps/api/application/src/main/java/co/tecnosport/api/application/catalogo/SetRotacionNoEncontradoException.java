package co.tecnosport.api.application.catalogo;

import java.util.UUID;

public final class SetRotacionNoEncontradoException extends RuntimeException {

  public SetRotacionNoEncontradoException(UUID id) {
    super("No existe un set de rotación con id '" + id + "'.");
  }
}
