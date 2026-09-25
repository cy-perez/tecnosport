package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.pedido.PedidosPaginados;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md.
 *
 * <p>Lo único que el borrado de un producto le pregunta al lado de los pedidos es si alguna
 * variante se vendió. Las otras once operaciones lanzan en vez de devolver un vacío educado: si un
 * cambio futuro las llama desde este controlador, que se note.
 */
final class RepositorioPedidosParaBorradoDobleDePrueba implements RepositorioPedidos {

  boolean hayVentas;

  void limpiar() {
    this.hayVentas = false;
  }

  @Override
  public boolean hayLineasDeAlgunaVariante(Collection<UUID> varianteIds) {
    return hayVentas;
  }

  @Override
  public Optional<Pedido> buscarPorId(UUID id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void guardar(Pedido pedido) {
    throw new UnsupportedOperationException();
  }

  @Override
  public NumeroPedido siguienteNumero(int anio) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PedidosPaginados buscarTodosPaginado(int pagina, int tamanoPagina, EstadoPedido estado) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean tieneRechazoEnEntrega(String correo) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Pedido> buscarSinAvisoDePlazo(
      Collection<EstadoPedido> estados, Instant creadosAntesDe) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean reclamarAvisoDePlazo(UUID pedidoId, Instant ahora) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Pedido> buscarSinComprobante(Collection<EstadoPedido> estados) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean reclamarComprobante(UUID pedidoId, Instant ahora) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void liberarComprobante(UUID pedidoId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void liberarAvisoDePlazo(UUID pedidoId) {
    throw new UnsupportedOperationException();
  }
}
