package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.proveedores.TallaAlProveedor;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ListarPedidosAdmin {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioProductos repositorioProductos;
  private final RepositorioCategorias repositorioCategorias;

  public ListarPedidosAdmin(
      RepositorioPedidos repositorioPedidos,
      RepositorioProductos repositorioProductos,
      RepositorioCategorias repositorioCategorias) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioCategorias = Objects.requireNonNull(repositorioCategorias);
  }

  public PedidosDelPanel ejecutar(ListarPedidosAdminComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    PedidosPaginados pedidos =
        repositorioPedidos.buscarTodosPaginado(
            comando.pagina(), comando.tamanoPagina(), comando.estado());
    Set<UUID> variantes = new LinkedHashSet<>();
    pedidos
        .items()
        .forEach(p -> p.lineas().stream().map(LineaPedido::varianteId).forEach(variantes::add));
    return new PedidosDelPanel(pedidos, tallasAlProveedor(variantes));
  }

  /**
   * Con la talla que cada variante tiene hoy, no con la del momento de vender: es la que el
   * proveedor tiene en su bodega (ADR-0085). Un producto se carga una vez aunque varias líneas sean
   * suyas, y una variante borrada no dice nada.
   */
  private Map<UUID, TallaAlProveedor> tallasAlProveedor(Set<UUID> variantes) {
    Map<UUID, TallaAlProveedor> tallas = new LinkedHashMap<>();
    Map<UUID, Producto> cargados = new HashMap<>();
    Map<UUID, List<String>> escalas = new HashMap<>();
    for (UUID varianteId : variantes) {
      Optional<Producto> producto =
          cargados.values().stream()
              .filter(p -> p.variantes().stream().anyMatch(v -> v.id().equals(varianteId)))
              .findFirst()
              .or(() -> repositorioProductos.buscarPorVarianteId(varianteId));
      if (producto.isEmpty() || producto.get().ajusteDeTalla().ninguno()) {
        producto.ifPresent(p -> cargados.putIfAbsent(p.id(), p));
        continue;
      }
      Producto suyo = producto.get();
      cargados.putIfAbsent(suyo.id(), suyo);
      List<String> escala = escalas.computeIfAbsent(suyo.id(), id -> escalaDe(suyo.categoria()));
      suyo.tallaAlProveedor(varianteId, escala).ifPresent(t -> tallas.put(varianteId, t));
    }
    return tallas;
  }

  /** La del repositorio y no la que trae el producto, que puede venir sin escala ni padre. */
  private List<String> escalaDe(Categoria delProducto) {
    return repositorioCategorias
        .buscarPorId(delProducto.id())
        .map(c -> c.escalaEfectiva(c.padreId().flatMap(repositorioCategorias::buscarPorId)))
        .orElse(List.of());
  }
}
