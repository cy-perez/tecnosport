package co.tecnosport.api.application.retracto;

import java.util.UUID;

public class SolicitudRetractoNoEncontradaException extends RuntimeException {

  public SolicitudRetractoNoEncontradaException(UUID id) {
    super("No existe la solicitud de retracto " + id + ".");
  }
}
