package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * La salida del pedido {@code PAGADO} que se quedó sin inventario confirmado: el pago llegó después
 * de que venciera la reserva y ya no había existencia. Hasta el 4 de octubre de 2026 ningún camino
 * del panel lo llevaba a preparación —solo el aplicador del pago y la conciliación de
 * transferencias hacían {@code PAGADO -> EN_PREPARACION}—, así que la única salida era cancelar y
 * perder la venta aunque la mercancía llegara al día siguiente.
 *
 * <p>Vuelve a confirmar cada línea (idempotente: las que ya estaban no se tocan) y, si todas
 * quedan, pasa a preparación. Si falta alguna, no cambia nada y lo dice.
 */
public final class ConfirmarInventarioDePedidoPagado {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public ConfirmarInventarioDePedidoPagado(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Pedido ejecutar(UUID pedidoId, String actor) {
    Objects.requireNonNull(pedidoId, "El pedido no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorIdParaModificar(pedidoId)
            .orElseThrow(() -> new PedidoNoEncontradoException(pedidoId));
    Instant ahora = reloj.ahora();
    // La transición se comprueba antes de tocar el inventario: confirmar la venta de un pedido que
    // no está pagado sería sacar mercancía sin dinero.
    if (pedido.estado() != EstadoPedido.PAGADO) {
      throw new TransicionDeEstadoInvalidaException(pedido.estado(), EstadoPedido.EN_PREPARACION);
    }
    if (!ConfirmarReservasDeLineas.confirmar(pedido.lineas(), ahora, repositorioInventario)) {
      throw new InventarioSinConfirmarException(pedidoId);
    }
    pedido.transicionar(EstadoPedido.EN_PREPARACION, actor, "inventario confirmado", ahora);
    repositorioPedidos.guardar(pedido);
    return pedido;
  }
}
