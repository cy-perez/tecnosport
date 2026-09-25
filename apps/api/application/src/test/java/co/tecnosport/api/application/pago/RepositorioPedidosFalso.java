package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Collection;
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

  /**
   * <b>Devuelve la instancia guardada, no una reconstruida</b>, a diferencia de los dobles de
   * inventario — y queda dicho porque la diferencia importa: {@code Pedido} es mutable, así que una
   * mutación sin {@code guardar} se ve aquí igual que una guardada. Los de inventario reconstituyen
   * en cada lectura a propósito, y su javadoc explica por qué: ahí el equivalente es sobreventa.
   *
   * <p>No se copia por una razón concreta, no por olvido: {@code Pedido} tiene catorce campos,
   * varios opcionales, y una copia a mano que se deje uno fuera falla en silencio y es peor que lo
   * que vendría a proteger. Hoy ningún caso de uso se olvida el {@code guardar}; el día que haga
   * falta la red, la forma de tenderla es un método de copia en el propio agregado, no un
   * constructor repetido en cuatro dobles.
   */
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

  @Override
  public java.util.List<co.tecnosport.api.domain.pedido.Pedido> buscarSinComprobante(
      java.util.Collection<co.tecnosport.api.domain.pedido.EstadoPedido> estados) {
    return java.util.List.of();
  }

  @Override
  public boolean reclamarComprobante(java.util.UUID pedidoId, java.time.Instant ahora) {
    return false;
  }

  @Override
  public void liberarComprobante(java.util.UUID pedidoId) {}

  @Override
  public void liberarAvisoDePlazo(UUID pedidoId) {}

  @Override
  public boolean hayLineasDeAlgunaVariante(Collection<UUID> varianteIds) {
    throw new UnsupportedOperationException();
  }
}
