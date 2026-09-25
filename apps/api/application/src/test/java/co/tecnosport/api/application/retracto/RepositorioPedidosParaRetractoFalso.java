package co.tecnosport.api.application.retracto;

import co.tecnosport.api.application.pedido.PedidosPaginados;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Solo lo que estos casos de uso usan. El doble de {@code application.pedido} no se puede
 * reutilizar desde aquí: es de visibilidad de paquete, a propósito.
 */
final class RepositorioPedidosParaRetractoFalso implements RepositorioPedidos {

  private final Map<UUID, Pedido> pedidos = new HashMap<>();

  void sembrar(Pedido pedido) {
    pedidos.put(pedido.id(), pedido);
  }

  @Override
  public Optional<Pedido> buscarPorId(UUID id) {
    return Optional.ofNullable(pedidos.get(id));
  }

  /**
   * Recorre lo guardado en vez de llevar un segundo indice: son unos pocos pedidos por prueba, y un
   * mapa mas seria un sitio mas donde los dos pueden separarse. Los dos criterios se filtran
   * juntos, como en la consulta de verdad.
   */
  @Override
  public Optional<Pedido> buscarPorNumeroYCorreo(NumeroPedido numero, String correoNormalizado) {
    return pedidos.values().stream()
        .filter(p -> p.numeroPedido().equals(numero))
        .filter(p -> p.correo().valor().equals(correoNormalizado))
        .findFirst();
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
