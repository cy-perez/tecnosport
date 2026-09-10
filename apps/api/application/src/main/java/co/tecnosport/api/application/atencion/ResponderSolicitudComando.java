package co.tecnosport.api.application.atencion;

import java.util.UUID;

public record ResponderSolicitudComando(UUID solicitudId, String resumen, String actor) {

  public ResponderSolicitudComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
