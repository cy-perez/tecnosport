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
  private UUID perdedorDelReclamo;

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
}
