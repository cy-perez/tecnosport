package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;

/** Un pedido detenido en un estado, con la fecha en que llegó a él según su historial. */
public record PedidoDetenido(Pedido pedido, Instant desde) {

  public PedidoDetenido {
    Objects.requireNonNull(pedido, "El pedido no puede ser nulo.");
    Objects.requireNonNull(desde, "La fecha de llegada no puede ser nula.");
  }
}
