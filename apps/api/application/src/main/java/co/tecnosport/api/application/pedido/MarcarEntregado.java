package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;

/**
 * Marca un pedido {@code DESPACHADO} como entregado (docs/11-pagos-y-envios.md). Un pedido
 * contraentrega no cobró nada al confirmar: entregarlo deja el recaudo pendiente, no solo
 * entregado, así que esta misma llamada encadena {@code ENTREGADO -> RECAUDO_PENDIENTE} cuando
 * corresponde — mismo criterio que advierte el javadoc de {@code EstadoPedido}: quien orquesta la
 * transición es responsable de no mezclar los dos caminos. Un pedido pagado en línea se queda en
 * {@code ENTREGADO}: su dinero ya entró antes del despacho.
 *
 * <p><b>Y es también donde el contraentrega confirma su reserva de inventario</b>, que hasta ahora
 * no ocurría en ninguna parte: {@code ConfirmarReservasDeLineas} solo se llamaba desde el pago de
 * Wompi aprobado y desde la transferencia conciliada, o sea desde los dos caminos por los que entra
 * dinero antes de despachar. Un contraentrega no pasa por ninguno, así que su reserva quedaba
 * abierta para siempre: la unidad vendida nunca salía del saldo total y el libro de movimientos se
 * quedaba sin la salida de una venta real.
 *
 * <p>Se confirma <b>al entregar y no al conciliar el recaudo</b>, aunque el dinero llegue después:
 * la mercancía sale del almacén cuando el comprador la recibe, y entre {@code ENTREGADO} y {@code
 * RECAUDO_CONCILIADO} pueden pasar semanas — un recaudo que nadie concilia dejaría abierto el mismo
 * agujero, más pequeño. El camino en que la entrega no ocurre ({@code RECHAZADO_EN_ENTREGA}) libera
 * la reserva por su cuenta desde {@code DESPACHADO}, así que los dos desenlaces de un despacho
 * resuelven su reserva y ninguno la deja colgando.
 *
 * <p>Las transiciones van antes de tocar el inventario, mismo criterio que {@code
 * RecibirProductoDevuelto} y {@code DespacharPedido}: un segundo intento se bloquea en la máquina
 * de estados y no llega a mover existencias dos veces.
 */
public final class MarcarEntregado {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public MarcarEntregado(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ResultadoEntrega ejecutar(MarcarEntregadoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    Instant ahora = reloj.ahora();
    pedido.transicionar(EstadoPedido.ENTREGADO, comando.actor(), "entregado", ahora);
    boolean inventarioConfirmado = true;
    if (pedido.metodoPago() == MetodoPago.CONTRAENTREGA) {
      pedido.transicionar(
          EstadoPedido.RECAUDO_PENDIENTE, comando.actor(), "recaudo pendiente de conciliar", ahora);
      inventarioConfirmado =
          ConfirmarReservasDeLineas.confirmar(pedido.lineas(), ahora, repositorioInventario);
    }
    repositorioPedidos.guardar(pedido);
    return new ResultadoEntrega(pedido, inventarioConfirmado);
  }
}
