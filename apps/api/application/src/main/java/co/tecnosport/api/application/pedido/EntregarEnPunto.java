package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;

/**
 * Entrega en el punto un pedido {@code LISTO_PARA_RECOGER} a quien muestre su número (ADR-0094).
 * Queda {@code ENTREGADO} con la fecha de hoy, y de ella corren el retracto y la garantía.
 *
 * <p>El número, el camino de entrega y el medio de pago los exige {@link Pedido#entregarEnPunto}:
 * si solo los mirara el panel, o este caso de uso, bastaría otra puerta para saltárselos.
 *
 * <p>En {@code PAGO_EN_PUNTO} el dinero entra en este acto y aquí se confirman las reservas, que
 * nadie confirmó antes porque no hubo pago antes —lo mismo que hace {@code MarcarEntregado} con la
 * contraentrega—. Un pedido pagado en línea ya las confirmó al entrar el dinero.
 */
public final class EntregarEnPunto {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public EntregarEnPunto(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public ResultadoEntrega ejecutar(EntregarEnPuntoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorIdParaModificar(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    Instant ahora = reloj.ahora();
    pedido.entregarEnPunto(
        comando.numeroPresentado(), comando.medioDePago(), comando.actor(), ahora);
    boolean inventarioConfirmado = true;
    if (pedido.metodoPago() == MetodoPago.PAGO_EN_PUNTO) {
      inventarioConfirmado =
          ConfirmarReservasDeLineas.confirmar(pedido.lineas(), ahora, repositorioInventario);
    }
    repositorioPedidos.guardar(pedido);
    return new ResultadoEntrega(pedido, inventarioConfirmado);
  }
}
