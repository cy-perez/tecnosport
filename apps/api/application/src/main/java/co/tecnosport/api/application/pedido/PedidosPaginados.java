package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.Pedido;
import java.util.List;
import java.util.Objects;

/**
 * Paginación por página, no por cursor (docs/03-api.md: "paginación por cursor en el catálogo, por
 * página en el panel administrativo").
 */
public record PedidosPaginados(
    List<Pedido> items, int pagina, int totalPaginas, long totalPedidos) {

  public PedidosPaginados {
    Objects.requireNonNull(items, "Los items no pueden ser nulos.");
    items = List.copyOf(items);
  }
}
