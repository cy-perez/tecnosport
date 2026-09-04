package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.pedido.PedidosPaginados;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioPedidosFalso implements RepositorioPedidos {

  private final Set<String> correosConRechazoEnEntrega = new HashSet<>();

  void conRechazoEnEntrega(String correo) {
    correosConRechazoEnEntrega.add(correo);
  }

  @Override
  public Optional<Pedido> buscarPorId(UUID id) {
    throw new UnsupportedOperationException("No usado por MetodosDePagoDisponibles.");
  }

  @Override
  public void guardar(Pedido pedido) {
    throw new UnsupportedOperationException("No usado por MetodosDePagoDisponibles.");
  }

  @Override
  public NumeroPedido siguienteNumero(int anio) {
    throw new UnsupportedOperationException("No usado por MetodosDePagoDisponibles.");
  }

  @Override
  public PedidosPaginados buscarTodosPaginado(int pagina, int tamanoPagina, EstadoPedido estado) {
    throw new UnsupportedOperationException("No usado por MetodosDePagoDisponibles.");
  }

  @Override
  public boolean tieneRechazoEnEntrega(String correo) {
    return correosConRechazoEnEntrega.contains(correo);
  }
}
