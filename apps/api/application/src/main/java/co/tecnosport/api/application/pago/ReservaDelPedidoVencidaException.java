package co.tecnosport.api.application.pago;

/**
 * Una línea del pedido ya no tiene su unidad apartada: la reserva venció (el pago en línea aparta
 * 30 minutos), o alguien la liberó. Cobrar así es cobrar una unidad que pudo venderse a otro
 * mientras tanto. Se responde 409, como la existencia insuficiente: el pedido no está mal, el
 * inventario cambió, y la salida es volver a crearlo —que vuelve a reservar contra la existencia de
 * ahora—.
 *
 * <p>El mensaje sale al cliente ({@code detail}): no nombra la variante ni el saldo, por lo mismo
 * que {@code ExistenciaInsuficienteException}.
 */
public final class ReservaDelPedidoVencidaException extends RuntimeException {

  public ReservaDelPedidoVencidaException() {
    super(
        "La reserva de este pedido venció y la unidad ya no está apartada. Crea el pedido de nuevo"
            + " para volver a reservarla.");
  }
}
