package co.tecnosport.api.application.catalogo;

import java.util.UUID;

public final class AtributoNoEncontradoException extends RuntimeException {

  public AtributoNoEncontradoException(UUID id) {
    super("No existe un atributo con id '" + id + "'.");
  }
}
