package co.tecnosport.api.application.reversion;

import java.util.UUID;

public class SolicitudReversionNoEncontradaException extends RuntimeException {

  public SolicitudReversionNoEncontradaException(UUID id) {
    super("No existe una solicitud de reversión con el id " + id + ".");
  }
}
