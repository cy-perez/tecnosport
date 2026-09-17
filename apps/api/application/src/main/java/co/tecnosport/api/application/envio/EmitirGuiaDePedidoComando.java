package co.tecnosport.api.application.envio;

import java.util.Objects;
import java.util.UUID;

/**
 * Qué pedido despachar y quién lo pidió. El actor no mueve el pedido —la emisión no lo transiciona—
 * pero sí queda en el registro: es la persona que comprometió el saldo de la cuenta.
 */
public record EmitirGuiaDePedidoComando(UUID pedidoId, String actor) {

  public EmitirGuiaDePedidoComando {
    Objects.requireNonNull(pedidoId, "El id del pedido no puede ser nulo.");
    Objects.requireNonNull(actor, "El actor no puede ser nulo.");
  }
}
