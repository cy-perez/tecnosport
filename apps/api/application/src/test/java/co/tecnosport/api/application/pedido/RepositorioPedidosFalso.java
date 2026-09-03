package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.Pedido;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioPedidosFalso implements RepositorioPedidos {

  private final Map<UUID, Pedido> pedidos = new HashMap<>();

  @Override
  public Optional<Pedido> buscarPorId(UUID id) {
    return Optional.ofNullable(pedidos.get(id));
  }

  @Override
  public void guardar(Pedido pedido) {
    pedidos.put(pedido.id(), pedido);
  }
}
