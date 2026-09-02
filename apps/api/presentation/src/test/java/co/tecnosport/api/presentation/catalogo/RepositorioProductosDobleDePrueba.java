package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.Optional;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
class RepositorioProductosDobleDePrueba implements RepositorioProductos {

  private List<Producto> productos = List.of();
  private ResultadoPaginado<Producto> resultadoBusqueda = new ResultadoPaginado<>(List.of(), null);

  void conProductos(Producto... productos) {
    this.productos = List.of(productos);
  }

  void devolverEnBusqueda(ResultadoPaginado<Producto> resultado) {
    this.resultadoBusqueda = resultado;
  }

  @Override
  public ResultadoPaginado<Producto> buscar(
      FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina) {
    return resultadoBusqueda;
  }

  @Override
  public Optional<Producto> buscarPorSlug(Slug slug) {
    return productos.stream().filter(producto -> producto.slug().equals(slug)).findFirst();
  }
}
