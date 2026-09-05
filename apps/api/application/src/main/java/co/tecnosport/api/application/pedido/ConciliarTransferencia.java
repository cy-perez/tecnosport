package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
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
 * <p>Encadena de una vez a {@code EN_PREPARACION}, mismo criterio que {@code
 * AplicadorDeResultadoDePago} con un pago de Wompi aprobado: no hay nada que verificar en un pago
 * ya conciliado, así que no tiene sentido pedir un segundo clic en el panel solo para avanzar el
 * estado.
 */
public final class ConciliarTransferencia {

  private final RepositorioPedidos repositorioPedidos;
  private final Reloj reloj;

  public ConciliarTransferencia(RepositorioPedidos repositorioPedidos, Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
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
    pedido.transicionar(
        EstadoPedido.EN_PREPARACION,
        comando.actor(),
        "pago conciliado, listo para preparar",
        ahora);
    repositorioPedidos.guardar(pedido);
    return pedido;
  }
}
