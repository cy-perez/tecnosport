package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.EstadoPedido;

/**
 * {@code estado} nulo lista todos los pedidos, más recientes primero. Filtrado por estado (por
 * ejemplo {@code RECAUDO_PENDIENTE}, docs/11-pagos-y-envios.md: "un pedido entregado hace veinte
 * días sin conciliar es plata en la calle"), ordena por más antiguo primero: lo más urgente arriba.
 */
public record ListarPedidosAdminComando(int pagina, int tamanoPagina, EstadoPedido estado) {

  public ListarPedidosAdminComando {
    if (pagina < 0) {
      throw new IllegalArgumentException("La página no puede ser negativa.");
    }
    if (tamanoPagina < 1 || tamanoPagina > 100) {
      throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y 100.");
    }
  }

  public ListarPedidosAdminComando(int pagina, int tamanoPagina) {
    this(pagina, tamanoPagina, null);
  }
}
