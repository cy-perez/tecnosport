package co.tecnosport.api.application.pedido;

import java.util.Objects;

public final class ListarPedidosAdmin {

  private final RepositorioPedidos repositorioPedidos;

  public ListarPedidosAdmin(RepositorioPedidos repositorioPedidos) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
  }

  public PedidosPaginados ejecutar(ListarPedidosAdminComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    return repositorioPedidos.buscarTodosPaginado(comando.pagina(), comando.tamanoPagina());
  }
}
