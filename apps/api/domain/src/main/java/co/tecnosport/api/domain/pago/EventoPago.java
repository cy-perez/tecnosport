package co.tecnosport.api.domain.pago;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Objects;

/**
 * Un evento de webhook recibido de Wompi. {@code idEvento} es el identificador que Wompi asigna a
 * cada notificación (docs/03-api.md): es lo que permite reconocer un reintento del webhook y no
 * aplicarlo dos veces.
 */
public record EventoPago(String idEvento, EstadoPago estado, Instant recibidoEn) {

  public EventoPago {
    if (idEvento == null || idEvento.isBlank()) {
      throw new ExcepcionDeDominio("El id de evento de pago no puede estar vacío.");
    }
    Objects.requireNonNull(estado, "El estado del evento no puede ser nulo.");
    Objects.requireNonNull(recibidoEn, "La fecha del evento no puede ser nula.");
  }
}
