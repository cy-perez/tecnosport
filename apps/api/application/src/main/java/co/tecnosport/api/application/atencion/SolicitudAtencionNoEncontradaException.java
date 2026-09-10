package co.tecnosport.api.application.atencion;

import java.util.UUID;

public class SolicitudAtencionNoEncontradaException extends RuntimeException {

  public SolicitudAtencionNoEncontradaException(UUID id) {
    super("No existe una solicitud de atención con el id " + id + ".");
  }

  public SolicitudAtencionNoEncontradaException(String radicado) {
    super("No existe una solicitud de atención con el radicado " + radicado + ".");
  }
}
