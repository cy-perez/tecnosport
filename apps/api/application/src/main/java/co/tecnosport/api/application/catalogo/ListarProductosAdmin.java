package co.tecnosport.api.application.catalogo;

import java.util.Objects;

/**
 * A diferencia de {@link BuscarProductos} (vitrina pública, solo {@code PUBLICADO}, cursor), el
 * panel ve todos los estados y pagina por página — mismo criterio que {@code ListarPedidosAdmin}.
 */
public final class ListarProductosAdmin {

  private final RepositorioProductos repositorioProductos;

  public ListarProductosAdmin(RepositorioProductos repositorioProductos) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
  }

  public ProductosPaginados ejecutar(ListarProductosAdminComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    return repositorioProductos.buscarParaAdmin(comando.pagina(), comando.tamanoPagina());
  }
}
