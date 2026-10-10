package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.EstadoPedido;

/**
 * El pedido tiene algo en juego —un estado que no es pago fallido ni cancelado, un pago, un envío o
 * un trámite— y no se borra. Lo que corresponde es cancelarlo; la historia se conserva.
 */
public final class PedidoNoEliminableException extends RuntimeException {

  private PedidoNoEliminableException(String mensaje) {
    super(mensaje);
  }

  public static PedidoNoEliminableException porEstado(EstadoPedido estado) {
    return new PedidoNoEliminableException(
        "Solo se elimina un pedido cancelado o con el pago fallido; este está en "
            + estado
            + ". Cancélalo en vez de eliminarlo.");
  }

  public static PedidoNoEliminableException porTransferenciaManual() {
    return new PedidoNoEliminableException(
        "Un pedido de transferencia manual no se elimina: el sistema no sabe si el dinero llegó."
            + " Se conserva cancelado.");
  }

  public static PedidoNoEliminableException porCompromisos() {
    return new PedidoNoEliminableException(
        "El pedido recibió un pago, tuvo un envío o tiene un trámite abierto, y su historia se"
            + " conserva. No se puede eliminar.");
  }
}
