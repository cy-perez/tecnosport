package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
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
 */
public final class MarcarEntregado {

  private final RepositorioPedidos repositorioPedidos;
  private final Reloj reloj;

  public MarcarEntregado(RepositorioPedidos repositorioPedidos, Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Pedido ejecutar(MarcarEntregadoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    Instant ahora = reloj.ahora();
    pedido.transicionar(EstadoPedido.ENTREGADO, comando.actor(), "entregado", ahora);
    if (pedido.metodoPago() == MetodoPago.CONTRAENTREGA) {
      pedido.transicionar(
          EstadoPedido.RECAUDO_PENDIENTE, comando.actor(), "recaudo pendiente de conciliar", ahora);
    }
    repositorioPedidos.guardar(pedido);
    return pedido;
  }
}
