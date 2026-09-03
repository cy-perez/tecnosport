package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pedido.EstadoPedido;

public final class PedidoNoEstaEnPagoPendienteException extends RuntimeException {

  public PedidoNoEstaEnPagoPendienteException(EstadoPedido estado) {
    super("El pedido está en estado " + estado + ", no admite un nuevo intento de pago.");
  }
}
