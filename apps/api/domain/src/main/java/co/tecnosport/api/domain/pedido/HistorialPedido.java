package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Un registro por transición: no se sobrescribe historia (docs/00-producto.md). */
public record HistorialPedido(
    UUID id, EstadoPedido estado, Instant fecha, String actor, String motivo) {

  public HistorialPedido {
    Objects.requireNonNull(id, "El id del registro de historial no puede ser nulo.");
    Objects.requireNonNull(estado, "El estado no puede ser nulo.");
    Objects.requireNonNull(fecha, "La fecha no puede ser nula.");
    if (actor == null || actor.isBlank()) {
      throw new ExcepcionDeDominio("El actor de una transición no puede estar vacío.");
    }
  }
}
