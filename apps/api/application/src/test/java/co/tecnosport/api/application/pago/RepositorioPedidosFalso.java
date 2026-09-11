package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioPedidosFalso implements RepositorioPedidos {

  private final Map<UUID, Pedido> pedidos = new HashMap<>();

  void conPedido(Pedido pedido) {
    pedidos.put(pedido.id(), pedido);
  }

  @Override
  public Optional<Pedido> buscarPorId(UUID id) {
    return Optional.ofNullable(pedidos.get(id));
  }

  @Override
  public void guardar(Pedido pedido) {
    pedidos.put(pedido.id(), pedido);
  }

  @Override
  public NumeroPedido siguienteNumero(int anio) {
    throw new UnsupportedOperationException("No usado por CrearIntentoDePago.");
  }

  @Override
  public co.tecnosport.api.application.pedido.PedidosPaginados buscarTodosPaginado(
      int pagina, int tamanoPagina, co.tecnosport.api.domain.pedido.EstadoPedido estado) {
    throw new UnsupportedOperationException("No usado por CrearIntentoDePago.");
  }

  @Override
  public boolean tieneRechazoEnEntrega(String correo) {
    throw new UnsupportedOperationException("No usado por CrearIntentoDePago.");
  }

  /** Ningún caso de uso de este paquete vigila plazos de entrega. */
  @Override
  public java.util.List<co.tecnosport.api.domain.pedido.Pedido> buscarSinAvisoDePlazo(
      java.util.Collection<co.tecnosport.api.domain.pedido.EstadoPedido> estados,
      java.time.Instant creadosAntesDe) {
    return java.util.List.of();
  }

  @Override
  public boolean reclamarAvisoDePlazo(java.util.UUID pedidoId, java.time.Instant ahora) {
    return false;
  }
}
