package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Objects;

/**
 * Verificación previa al despacho de un pedido contraentrega (docs/11-pagos-y-envios.md): contacto
 * por WhatsApp o llamada antes de despachar, con el motivo que registra el administrador.
 *
 * <p>Solo un pedido {@code CONFIRMADO_CONTRAENTREGA} que se paga al recibir —{@code CONTRAENTREGA}
 * o {@code PAGO_EN_PUNTO}, ADR-0094—. Los dos se verifican igual: una llamada o un WhatsApp antes
 * de prepararlo. La guarda es explícita y no se deja al grafo de estados: el grafo permite {@code
 * PAGADO → EN_PREPARACION}, y sin ella un pedido pagado pasaba a preparación por aquí sin que
 * {@code ConfirmarInventarioDePedidoPagado} confirmara su reserva.
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
            .buscarPorIdParaModificar(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    if (pedido.estado() != EstadoPedido.CONFIRMADO_CONTRAENTREGA
        || !pedido.metodoPago().seCobraAlRecibir()) {
      throw new ContraentregaNoVerificableException(
          pedido.id(), pedido.estado(), pedido.metodoPago());
    }
    pedido.transicionar(
        EstadoPedido.EN_PREPARACION, comando.actor(), comando.motivo(), reloj.ahora());
    repositorioPedidos.guardar(pedido);
    return pedido;
  }
}
