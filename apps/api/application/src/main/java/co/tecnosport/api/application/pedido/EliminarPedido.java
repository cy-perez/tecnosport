package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Objects;
import java.util.UUID;

/**
 * Borra del todo un pedido en el que nunca hubo nada en juego (ADR-0077): ni venta, ni envío, ni
 * trámite. Es la limpieza de los pedidos de prueba y de los que murieron en el pago; todo lo demás
 * se cancela y conserva su historia.
 *
 * <p><b>Dos condiciones, y las dos son lecturas previas</b>, como en {@code EliminarProducto}: la
 * diferencia entre un 409 que el panel explica y un 500 con una violación de llave foránea dentro.
 *
 * <ul>
 *   <li><b>El estado</b> lo decide el dominio ({@code EstadoPedido.admiteEliminacion}): pago
 *       fallido o cancelado. Ahí la reserva de inventario ya se liberó.
 *   <li><b>Lo que cuelga de él</b> lo mira {@link BorradoDePedidos}: un pago aprobado o pendiente,
 *       un envío o una emisión de guía, un retracto, un reintegro, una PQR, una garantía o una
 *       reversión. Un pedido cancelado después de cobrar tiene su pago y su reintegro, y la
 *       política de datos publicada promete conservar la compra pagada por la ley tributaria.
 * </ul>
 *
 * <p><b>La transferencia manual no se elimina nunca.</b> Su dinero no deja fila en {@code pago}: el
 * comprobante llega por WhatsApp y la conciliación solo mueve el estado. Un pedido de transferencia
 * cancelado puede tener una consignación que nadie vio, y el sistema no tiene cómo saberlo;
 * borrarlo dejaría ese ingreso sin pedido.
 *
 * <p><b>Con la fila bloqueada</b> ({@code buscarPorIdParaModificar}), como todo caso de uso que
 * cambia el pedido: sin el bloqueo, un "reintentar pago" que confirma entre la lectura y el borrado
 * dejaría un pago pendiente en Wompi con una referencia que ya no existe.
 *
 * <p><b>Rompe a propósito "no se sobrescribe historia"</b> (docs/00-producto.md) para estos
 * pedidos: el historial se va con el pedido. Lo que queda es la línea de registro del controlador,
 * con el número y quién lo borró.
 */
public final class EliminarPedido {

  private final RepositorioPedidos repositorioPedidos;
  private final BorradoDePedidos borradoDePedidos;

  public EliminarPedido(RepositorioPedidos repositorioPedidos, BorradoDePedidos borradoDePedidos) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.borradoDePedidos = Objects.requireNonNull(borradoDePedidos);
  }

  /** Devuelve el número del pedido borrado, para que quien llame lo registre. */
  public NumeroPedido ejecutar(UUID pedidoId) {
    Objects.requireNonNull(pedidoId, "El id del pedido no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorIdParaModificar(pedidoId)
            .orElseThrow(() -> new PedidoNoEncontradoException(pedidoId));
    if (!pedido.estado().admiteEliminacion()) {
      throw PedidoNoEliminableException.porEstado(pedido.estado());
    }
    if (pedido.metodoPago() == MetodoPago.TRANSFERENCIA_MANUAL) {
      throw PedidoNoEliminableException.porTransferenciaManual();
    }
    if (!borradoDePedidos.compromisosDe(pedidoId).isEmpty()) {
      throw PedidoNoEliminableException.porCompromisos();
    }
    borradoDePedidos.eliminar(pedidoId);
    return pedido.numeroPedido();
  }
}
