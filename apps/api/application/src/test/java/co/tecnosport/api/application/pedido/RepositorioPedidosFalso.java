package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioPedidosFalso implements RepositorioPedidos {

  private final Map<UUID, Pedido> pedidos = new HashMap<>();
  private final Map<Integer, Long> secuenciasPorAnio = new HashMap<>();
  private final List<UUID> reclamos = new ArrayList<>();
  private final Set<UUID> avisados = new HashSet<>();
  private final Set<UUID> comprobados = new HashSet<>();
  private UUID perdedorDelReclamo;

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

  /** Para afirmar que un caso de uso que falló no dejó ningún pedido guardado. */
  List<Pedido> todos() {
    return List.copyOf(pedidos.values());
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
  public List<Pedido> buscarSinAvisoDePlazo(
      Collection<EstadoPedido> estados, Instant creadosAntesDe) {
    return pedidos.values().stream()
        .filter(p -> estados.contains(p.estado()))
        .filter(p -> p.avisoDePlazoEnviadoEn().isEmpty() && !avisados.contains(p.id()))
        .filter(p -> p.creadoEn().isBefore(creadosAntesDe))
        .sorted(Comparator.comparing(Pedido::creadoEn))
        .toList();
  }

  /**
   * El reclamo de verdad: solo lo gana quien llegue primero, igual que el {@code where ... is null}
   * de Postgres. {@code perdedorDelReclamo} simula a otra instancia que se adelantó — es lo que
   * hace comprobable que el caso de uso no escriba cuando pierde.
   */
  @Override
  public boolean reclamarAvisoDePlazo(UUID pedidoId, Instant ahora) {
    reclamos.add(pedidoId);
    if (pedidoId.equals(perdedorDelReclamo)) {
      return false;
    }
    return avisados.add(pedidoId);
  }

  @Override
  public List<Pedido> buscarSinComprobante(Collection<EstadoPedido> estados) {
    return pedidos.values().stream()
        .filter(p -> estados.contains(p.estado()))
        .filter(p -> !comprobados.contains(p.id()))
        .sorted(Comparator.comparing(Pedido::creadoEn))
        .toList();
  }

  /** El mismo reclamo de verdad del aviso de plazo, sobre su propia marca. */
  @Override
  public boolean reclamarComprobante(UUID pedidoId, Instant ahora) {
    reclamos.add(pedidoId);
    if (pedidoId.equals(perdedorDelReclamo)) {
      return false;
    }
    return comprobados.add(pedidoId);
  }

  /** Devolver el reclamo, que es lo que permite que la vuelta siguiente lo reintente. */
  @Override
  public void liberarComprobante(UUID pedidoId) {
    comprobados.remove(pedidoId);
  }

  /** Lo mismo para el aviso de plazo, y por el mismo motivo. */
  @Override
  public void liberarAvisoDePlazo(UUID pedidoId) {
    avisados.remove(pedidoId);
  }

  /** Que otra instancia le gane el reclamo a ese pedido. */
  void queOtroGaneElReclamoDe(UUID pedidoId) {
    this.perdedorDelReclamo = pedidoId;
  }

  List<UUID> reclamos() {
    return List.copyOf(reclamos);
  }

  @Override
  public boolean tieneRechazoEnEntrega(String correo) {
    return pedidos.values().stream()
        .anyMatch(
            p ->
                p.estado() == EstadoPedido.RECHAZADO_EN_ENTREGA
                    && p.correo().valor().equals(correo));
  }

  @Override
  public boolean hayLineasDeAlgunaVariante(Collection<UUID> varianteIds) {
    throw new UnsupportedOperationException();
  }
}
