package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.EnvioNoEncontradoException;
import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;

/**
 * Concilia el recaudo de un pedido {@code RECAUDO_PENDIENTE} (docs/11-pagos-y-envios.md): la
 * transportadora consignó, y su comisión se registra en el {@code Envio} del despacho. La
 * transición del pedido se aplica primero, igual que {@code DespacharPedido}: un segundo intento
 * sobre un pedido ya conciliado ({@code RECAUDO_CONCILIADO} es terminal) se bloquea antes de tocar
 * el envío de nuevo.
 */
public final class ConciliarRecaudo {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioEnvios repositorioEnvios;
  private final Reloj reloj;

  public ConciliarRecaudo(
      RepositorioPedidos repositorioPedidos, RepositorioEnvios repositorioEnvios, Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioEnvios = Objects.requireNonNull(repositorioEnvios);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Pedido ejecutar(ConciliarRecaudoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    Instant ahora = reloj.ahora();
    pedido.transicionar(
        EstadoPedido.RECAUDO_CONCILIADO, comando.actor(), "recaudo conciliado", ahora);
    Envio envio =
        repositorioEnvios
            .buscarPorPedidoId(pedido.id())
            .orElseThrow(() -> new EnvioNoEncontradoException(pedido.id()));
    envio.conciliarRecaudo(comando.comisionRecaudo(), ahora);
    repositorioPedidos.guardar(pedido);
    repositorioEnvios.guardar(envio);
    return pedido;
  }
}
