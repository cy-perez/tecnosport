package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;

/**
 * Despacha un pedido en {@code EN_PREPARACION} (docs/11-pagos-y-envios.md): transportadora y guía.
 * La transición se aplica primero — un {@code Envio} nunca queda huérfano de un despacho que en
 * realidad falló porque el pedido no estaba en {@code EN_PREPARACION} (un contraentrega sin
 * verificar todavía, o un segundo intento de despachar el mismo pedido).
 */
public final class DespacharPedido {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioEnvios repositorioEnvios;
  private final Reloj reloj;

  public DespacharPedido(
      RepositorioPedidos repositorioPedidos, RepositorioEnvios repositorioEnvios, Reloj reloj) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioEnvios = Objects.requireNonNull(repositorioEnvios);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Pedido ejecutar(DespacharPedidoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    Instant ahora = reloj.ahora();
    pedido.transicionar(
        EstadoPedido.DESPACHADO,
        comando.actor(),
        "despachado con " + comando.transportadora(),
        ahora);
    Envio envio =
        Envio.crear(
            pedido.id(), comando.transportadora(), comando.guia(), comando.costoEnvio(), ahora);
    repositorioPedidos.guardar(pedido);
    repositorioEnvios.guardar(envio);
    return pedido;
  }
}
