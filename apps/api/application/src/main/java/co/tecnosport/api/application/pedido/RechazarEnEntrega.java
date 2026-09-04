package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;

/**
 * Un pedido rechazado en la entrega libera cada línea reservada (docs/11-pagos-y-envios.md; {@code
 * LineaPedido.idReserva} identifica cuál movimiento liberar, sin adivinar por variante y cantidad).
 * La transición se aplica primero, igual que {@code DespacharPedido}: un segundo intento sobre un
 * pedido ya rechazado ({@code RECHAZADO_EN_ENTREGA} es terminal) se bloquea antes de tocar
 * inventario de nuevo.
 */
public final class RechazarEnEntrega {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public RechazarEnEntrega(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
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
    for (LineaPedido linea : pedido.lineas()) {
      liberarReserva(linea, comando.motivo(), ahora);
    }
    repositorioPedidos.guardar(pedido);
    return pedido;
  }

  private void liberarReserva(LineaPedido linea, String motivo, Instant ahora) {
    Inventario inventario =
        repositorioInventario
            .buscarPorVarianteId(linea.varianteId())
            .orElseThrow(() -> new VarianteNoEncontradaException(linea.varianteId()));
    inventario.liberar(linea.idReserva(), motivo, ahora);
    repositorioInventario.guardar(inventario);
  }
}
