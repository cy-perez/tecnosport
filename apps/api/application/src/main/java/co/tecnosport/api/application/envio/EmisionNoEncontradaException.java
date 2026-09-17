package co.tecnosport.api.application.envio;

import java.util.UUID;

public final class EmisionNoEncontradaException extends RuntimeException {

  public EmisionNoEncontradaException(UUID emisionId) {
    super("No existe la emisión " + emisionId + ".");
  }
}
