package co.tecnosport.api.application.atencion;

import co.tecnosport.api.domain.atencion.TipoSolicitud;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code recibidaEn} lo escribe quien radica, mirando la fecha del correo o del mensaje: es el dato
 * del que cuelga el plazo, y no puede ser "ahora" por omisión sin convertir el registro tardío en
 * una forma de no incumplir nunca.
 */
public record RadicarSolicitudComando(
    TipoSolicitud tipo,
    String correo,
    UUID pedidoId,
    Instant recibidaEn,
    String asunto,
    String actor) {

  public RadicarSolicitudComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
