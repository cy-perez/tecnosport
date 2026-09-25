package co.tecnosport.api.presentation.retracto;

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

final class RepositorioPedidosDobleDePrueba implements RepositorioPedidos {

  private final Map<UUID, Pedido> pedidos = new HashMap<>();

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
    rechazarSiElNumeroYaEsDeOtro(pedido);
    pedidos.put(pedido.id(), pedido);
  }

  /**
   * El número de pedido es único en el repositorio real. Sin esta comprobación el doble acepta dos
   * pedidos con el mismo número y las consultas que recorren todo lo guardado empiezan a contar
   * pedidos que montó otra prueba, una de cada tantas corridas.
   */
  private void rechazarSiElNumeroYaEsDeOtro(Pedido pedido) {
    boolean ocupado =
        pedidos.values().stream()
            .anyMatch(
                otro ->
                    otro.numeroPedido().equals(pedido.numeroPedido())
                        && !otro.id().equals(pedido.id()));
    if (ocupado) {
      throw new IllegalStateException(
          "Ya hay otro pedido con el número "
              + pedido.numeroPedido().valor()
              + " en el doble de prueba. Si es el montaje de otra prueba, falta limpiarlo entre"
              + " métodos; si la prueba necesita dos pedidos, dales números distintos.");
    }
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
