package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Objects;

/**
 * Verificación previa al despacho de un pedido contraentrega (docs/11-pagos-y-envios.md): contacto
 * por WhatsApp o llamada antes de despachar, con el motivo que registra el administrador.
 *
 * <p>{@code CONFIRMADO_CONTRAENTREGA} solo lo alcanza un pedido con {@code
 * MetodoPago.CONTRAENTREGA} ({@code Pedido.crear} lo fija según el método al confirmar), así que no
 * hace falta revalidar el método de pago aparte, a diferencia de {@code ConciliarTransferencia}.
 */
public final class VerificarContraentrega {

  private final RepositorioPedidos repositorioPedidos;
  private final Reloj reloj;

  public VerificarContraentrega(RepositorioPedidos repositorioPedidos, Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Pedido ejecutar(VerificarContraentregaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    pedido.transicionar(
        EstadoPedido.EN_PREPARACION, comando.actor(), comando.motivo(), reloj.ahora());
    repositorioPedidos.guardar(pedido);
    return pedido;
  }
}
