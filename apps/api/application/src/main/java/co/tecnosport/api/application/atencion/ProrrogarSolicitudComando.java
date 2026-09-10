package co.tecnosport.api.application.atencion;

import java.util.UUID;

/**
 * Sin fecha de aviso: la pone el caso de uso al enviar el correo, porque el aviso es lo que hace
 * válida la prórroga y no puede quedar a lo que teclee quien la registra.
 */
public record ProrrogarSolicitudComando(UUID solicitudId, String motivo, String actor) {

  public ProrrogarSolicitudComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
