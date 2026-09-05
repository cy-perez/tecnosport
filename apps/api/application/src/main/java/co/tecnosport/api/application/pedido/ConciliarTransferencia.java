package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;

/**
 * El administrador concilia el comprobante de una transferencia manual (docs/11-pagos-y-envios.md:
 * "El administrador concilia el comprobante en el panel"). Solo pedidos {@code
 * TRANSFERENCIA_MANUAL}: uno de Wompi nunca se marca pagado por una acción manual del panel — su
 * verdad es siempre el webhook firmado o la conciliación programada (docs/00-producto.md: el
 * servidor no confía en el cliente para el estado de pago).
 *
 * <p>Un pedido que ya no está en {@code PAGO_PENDIENTE} (ya conciliado, o en cualquier otro estado)
 * rechaza la transición por su cuenta ({@code Pedido.transicionar}) — no hace falta comprobarlo
 * aparte, y de paso un doble clic en el panel no duplica nada.
 *
 * <p>Encadena a {@code EN_PREPARACION} solo si {@link ConfirmarReservasDeLineas} confirma la
 * reserva de cada línea, mismo criterio que {@code AplicadorDeResultadoDePago} con un pago de Wompi
 * aprobado: la reserva de una transferencia sí vence por tiempo (docs/02-modelo-datos.md), así que
 * conciliar el comprobante no basta por sí solo — si la reserva ya venció o se liberó, la unidad
 * pudo haberse vendido a otro comprador y el pedido se queda en {@code PAGADO} para revisión manual
 * en vez de decirle al almacén que prepare algo con un inventario en duda.
 */
public final class ConciliarTransferencia {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public ConciliarTransferencia(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Pedido ejecutar(ConciliarTransferenciaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    if (pedido.metodoPago() != MetodoPago.TRANSFERENCIA_MANUAL) {
      throw new MetodoDePagoNoEsTransferenciaManualException(pedido.metodoPago());
    }
    Instant ahora = reloj.ahora();
    pedido.transicionar(
        EstadoPedido.PAGADO, comando.actor(), "comprobante de transferencia conciliado", ahora);
    boolean inventarioOk =
        ConfirmarReservasDeLineas.confirmar(pedido.lineas(), ahora, repositorioInventario);
    if (inventarioOk) {
      pedido.transicionar(
          EstadoPedido.EN_PREPARACION,
          comando.actor(),
          "pago conciliado, listo para preparar",
          ahora);
    }
    repositorioPedidos.guardar(pedido);
    return pedido;
  }
}
