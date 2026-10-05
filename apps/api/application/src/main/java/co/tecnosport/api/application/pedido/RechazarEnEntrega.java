package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;

/**
 * La transportadora no pudo entregar y el paquete viene de vuelta (docs/11-pagos-y-envios.md).
 *
 * <p><b>No toca el inventario, y hasta el 4 de octubre de 2026 sí lo hacía.</b> Liberaba cada
 * reserva en el acto, y eso estaba mal de dos maneras. En un pedido pagado la reserva ya tenía
 * {@code SALIDA}, así que {@code Inventario.liberar} lanzaba; como esto corre dentro de la
 * conciliación de envíos, la excepción revertía la corrida entera en cada vuelta y ningún otro
 * envío volvía a registrar su entrega. Y en contraentrega, donde sí liberaba, ponía a la venta una
 * unidad que seguía en el camión.
 *
 * <p>La unidad vuelve cuando vuelve: {@link RecibirPedidoRechazado} la reingresa y, si el dinero ya
 * había entrado, deja la constancia del reintegro.
 */
public final class RechazarEnEntrega {

  private final RepositorioPedidos repositorioPedidos;
  private final Reloj reloj;

  public RechazarEnEntrega(RepositorioPedidos repositorioPedidos, Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Pedido ejecutar(RechazarEnEntregaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    Instant ahora = reloj.ahora();
    pedido.transicionar(
        EstadoPedido.RECHAZADO_EN_ENTREGA, comando.actor(), comando.motivo(), ahora);
    repositorioPedidos.guardar(pedido);
    return pedido;
  }
}
