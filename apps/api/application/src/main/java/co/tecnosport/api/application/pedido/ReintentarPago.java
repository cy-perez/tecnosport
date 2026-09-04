package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Objects;

/**
 * Regresa un pedido {@code PAGO_FALLIDO} a {@code PAGO_PENDIENTE} (docs/02-modelo-datos.md ya
 * contempla esa transición) para que el cliente pueda pedir un intento de pago nuevo con {@code
 * CrearIntentoDePago} — ese caso de uso deliberadamente no acepta pedidos en {@code PAGO_FALLIDO}
 * directo, así que hacía falta este paso aparte.
 *
 * <p>Solo un pedido procesado por Wompi llega a {@code PAGO_FALLIDO}: transferencia manual y
 * contraentrega nunca pasan por {@code AplicadorDeResultadoDePago}. El estado ya lo garantiza, así
 * que no hace falta revalidar el método de pago aparte.
 */
public final class ReintentarPago {

  private final RepositorioPedidos repositorioPedidos;
  private final Reloj reloj;

  public ReintentarPago(RepositorioPedidos repositorioPedidos, Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Pedido ejecutar(ReintentarPagoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    pedido.transicionar(
        EstadoPedido.PAGO_PENDIENTE, pedido.correo().valor(), "reintento de pago", reloj.ahora());
    repositorioPedidos.guardar(pedido);
    return pedido;
  }
}
