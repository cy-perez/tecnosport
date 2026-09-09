package co.tecnosport.api.presentation.retracto;

import co.tecnosport.api.application.pedido.PedidosPaginados;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class RepositorioPedidosDobleDePrueba implements RepositorioPedidos {

  private final Map<UUID, Pedido> pedidos = new HashMap<>();

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

  void limpiar() {
    pedidos.clear();
  }
}
