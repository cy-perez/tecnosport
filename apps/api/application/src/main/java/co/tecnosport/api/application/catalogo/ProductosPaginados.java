package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Producto;
import java.util.List;
import java.util.Objects;

/**
 * Paginación por página, no por cursor (docs/03-api.md: "paginación por cursor en el catálogo, por
 * página en el panel administrativo") — mismo criterio que {@code
 * co.tecnosport.api.application.pedido.PedidosPaginados}.
 */
public record ProductosPaginados(
    List<Producto> items, int pagina, int totalPaginas, long totalProductos) {

  public ProductosPaginados {
    Objects.requireNonNull(items, "Los items no pueden ser nulos.");
    items = List.copyOf(items);
  }
}
