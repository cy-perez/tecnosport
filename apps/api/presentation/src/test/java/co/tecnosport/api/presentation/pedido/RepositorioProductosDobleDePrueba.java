package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class RepositorioProductosDobleDePrueba implements RepositorioProductos {

  private List<Producto> productos = List.of();

  void conProductos(Producto... productos) {
    this.productos = List.of(productos);
  }

  @Override
  public ResultadoPaginado<Producto> buscar(
      FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina) {
    return new ResultadoPaginado<>(List.of(), null);
  }

  @Override
  public Optional<Producto> buscarPorSlug(Slug slug) {
    return productos.stream().filter(producto -> producto.slug().equals(slug)).findFirst();
  }

  @Override
  public Optional<Producto> buscarPorVarianteId(UUID varianteId) {
    return productos.stream()
        .filter(producto -> producto.variantes().stream().anyMatch(v -> v.id().equals(varianteId)))
        .findFirst();
  }

  @Override
  public ProductosPaginados buscarParaAdmin(int pagina, int tamanoPagina) {
    return new ProductosPaginados(List.of(), 0, 0, 0);
  }

  @Override
  public void guardar(Producto producto) {
    throw new UnsupportedOperationException("No usado por PedidoControladorTest.");
  }

  @Override
  public Optional<Producto> buscarPorId(UUID id) {
    throw new UnsupportedOperationException("No usado por PedidoControladorTest.");
  }

  @Override
  public void actualizar(Producto producto) {
    throw new UnsupportedOperationException("No usado por PedidoControladorTest.");
  }
}
