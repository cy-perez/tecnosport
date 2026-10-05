package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Objects;

/** Un pago aprobado sin pedido que lo esperara, con el pedido al que apuntaba, para el panel. */
public record PagoSinPedido(Pago pago, Pedido pedido) {

  public PagoSinPedido {
    Objects.requireNonNull(pago, "El pago no puede ser nulo.");
    Objects.requireNonNull(pedido, "El pedido no puede ser nulo.");
  }
}
