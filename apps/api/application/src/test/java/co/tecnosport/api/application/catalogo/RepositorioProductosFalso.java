package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.Optional;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioProductosFalso implements RepositorioProductos {

  private List<Producto> productos = List.of();
  private ResultadoPaginado<Producto> resultadoBusqueda = new ResultadoPaginado<>(List.of(), null);

  FiltroProductos ultimoFiltro;
  OrdenProductos ultimoOrden;
  String ultimoCursor;
  int ultimoTamanoPagina;

  void conProductos(Producto... productos) {
    this.productos = List.of(productos);
  }

  void devolverEnBusqueda(ResultadoPaginado<Producto> resultado) {
    this.resultadoBusqueda = resultado;
  }

  @Override
  public ResultadoPaginado<Producto> buscar(
      FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina) {
    this.ultimoFiltro = filtro;
    this.ultimoOrden = orden;
    this.ultimoCursor = cursor;
    this.ultimoTamanoPagina = tamanoPagina;
    return resultadoBusqueda;
  }

  @Override
  public Optional<Producto> buscarPorSlug(Slug slug) {
    return productos.stream().filter(producto -> producto.slug().equals(slug)).findFirst();
  }
}
