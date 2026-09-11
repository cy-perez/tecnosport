package co.tecnosport.api.application.reversion;

import co.tecnosport.api.application.pedido.PedidosPaginados;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Solo lo que estos casos de uso usan. El doble de {@code application.pedido} no se puede
 * reutilizar desde aquí: es de visibilidad de paquete, a propósito.
 */
final class RepositorioPedidosFalsoReversion implements RepositorioPedidos {

  private final Map<UUID, Pedido> pedidos = new HashMap<>();

  void sembrar(Pedido pedido) {
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
    throw new UnsupportedOperationException("no usado en estas pruebas");
  }

  @Override
  public PedidosPaginados buscarTodosPaginado(int pagina, int tamanoPagina, EstadoPedido estado) {
    throw new UnsupportedOperationException("no usado en estas pruebas");
  }

  @Override
  public boolean tieneRechazoEnEntrega(String correo) {
    return false;
  }

  /** Ningún caso de uso de este paquete vigila plazos de entrega. */
  @Override
  public java.util.List<co.tecnosport.api.domain.pedido.Pedido> buscarSinAvisoDePlazo(
      java.util.Collection<co.tecnosport.api.domain.pedido.EstadoPedido> estados,
      java.time.Instant creadosAntesDe) {
    return java.util.List.of();
  }
}
