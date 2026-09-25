package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.pedido.PedidosPaginados;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md.
 *
 * <p>De las trece operaciones del puerto, el borrado de un producto usa una. Las otras doce lanzan
 * en vez de devolver un vacío educado: si un cambio futuro las llama desde aquí, que se note.
 */
final class RepositorioPedidosParaBorradoFalso implements RepositorioPedidos {

  private final Set<UUID> variantesVendidas = new java.util.HashSet<>();

  /** Lo que se preguntó, para poder afirmar que se preguntó por todas y no solo por la primera. */
  final List<Collection<UUID>> consultas = new ArrayList<>();

  void conVarianteVendida(UUID varianteId) {
    variantesVendidas.add(varianteId);
  }

  @Override
  public boolean hayLineasDeAlgunaVariante(Collection<UUID> varianteIds) {
    consultas.add(List.copyOf(varianteIds));
    return varianteIds.stream().anyMatch(variantesVendidas::contains);
  }

  @Override
  public Optional<Pedido> buscarPorId(UUID id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<Pedido> buscarPorNumero(NumeroPedido numero) {
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
