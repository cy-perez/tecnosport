package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.pedido.PedidosPaginados;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class RepositorioPedidosDobleDePrueba implements RepositorioPedidos {

  // LinkedHashMap y no HashMap: buscarTodosPaginado ordena por creadoEn con un sort estable, así
  // que dos pedidos del mismo instante conservan el orden del mapa. Con HashMap ese orden lo
  // deciden los hashes de unos UUID aleatorios y cambia en cada corrida.
  private final Map<UUID, Pedido> pedidos = new LinkedHashMap<>();
  private final Map<Integer, Long> secuenciasPorAnio = new HashMap<>();

  void limpiar() {
    pedidos.clear();
    secuenciasPorAnio.clear();
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
    rechazarSiElNumeroYaEsDeOtro(pedido);
    pedidos.put(pedido.id(), pedido);
  }

  /**
   * El número de pedido es único en el repositorio real. Sin esta comprobación el doble acepta dos
   * pedidos con el mismo número y las consultas que recorren todo lo guardado —la paginación del
   * panel, o tieneRechazoEnEntrega, que mira el correo— empiezan a contar pedidos que montó otra
   * prueba. Que falle aquí, al sembrar, deja el descuido a la vista en vez de convertirlo en una
   * prueba que a veces pasa.
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
    long secuencial = secuenciasPorAnio.merge(anio, 1L, Long::sum);
    return NumeroPedido.de(anio, secuencial);
  }

  @Override
  public PedidosPaginados buscarTodosPaginado(int pagina, int tamanoPagina, EstadoPedido estado) {
    List<Pedido> filtrados =
        pedidos.values().stream()
            .filter(p -> estado == null || p.estado() == estado)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    filtrados.sort(
        estado == null
            ? Comparator.comparing(Pedido::creadoEn).reversed()
            : Comparator.comparing(Pedido::creadoEn));
    int desde = pagina * tamanoPagina;
    List<Pedido> contenido =
        desde >= filtrados.size()
            ? List.of()
            : filtrados.subList(desde, Math.min(desde + tamanoPagina, filtrados.size()));
    int totalPaginas = (int) Math.ceil(filtrados.size() / (double) tamanoPagina);
    return new PedidosPaginados(List.copyOf(contenido), pagina, totalPaginas, filtrados.size());
  }

  @Override
  public boolean tieneRechazoEnEntrega(String correo) {
    return pedidos.values().stream()
        .anyMatch(
            p ->
                p.estado() == EstadoPedido.RECHAZADO_EN_ENTREGA
                    && p.correo().valor().equals(correo));
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
