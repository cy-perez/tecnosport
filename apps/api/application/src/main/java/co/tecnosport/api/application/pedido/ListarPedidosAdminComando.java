package co.tecnosport.api.application.pedido;

public record ListarPedidosAdminComando(int pagina, int tamanoPagina) {

  public ListarPedidosAdminComando {
    if (pagina < 0) {
      throw new IllegalArgumentException("La página no puede ser negativa.");
    }
    if (tamanoPagina < 1 || tamanoPagina > 100) {
      throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y 100.");
    }
  }
}
