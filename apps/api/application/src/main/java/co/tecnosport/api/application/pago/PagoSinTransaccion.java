package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Objects;

/** Un pago de Wompi pendiente y sin id de transacción, con su pedido, para la alerta del panel. */
public record PagoSinTransaccion(Pago pago, Pedido pedido) {

  public PagoSinTransaccion {
    Objects.requireNonNull(pago, "El pago no puede ser nulo.");
    Objects.requireNonNull(pedido, "El pedido no puede ser nulo.");
  }
}
