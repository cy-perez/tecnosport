package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.pedido.PedidosPaginados;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class RepositorioPedidosDobleDePrueba implements RepositorioPedidos {

  private final Map<UUID, Pedido> pedidos = new HashMap<>();
  private final Map<Integer, Long> secuenciasPorAnio = new HashMap<>();

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
}
