package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioProductosFalso implements RepositorioProductos {

  private List<Producto> productos = new ArrayList<>();
  private ResultadoPaginado<Producto> resultadoBusqueda = new ResultadoPaginado<>(List.of(), null);
  private ProductosPaginados resultadoAdmin = new ProductosPaginados(List.of(), 0, 0, 0);

  FiltroProductos ultimoFiltro;
  OrdenProductos ultimoOrden;
  String ultimoCursor;
  int ultimoTamanoPagina;
  int ultimaPaginaAdmin;
  int ultimoTamanoPaginaAdmin;
  Producto ultimoGuardado;
  Producto ultimoActualizado;

  void conProductos(Producto... productos) {
    this.productos = List.of(productos);
  }

  void devolverEnBusqueda(ResultadoPaginado<Producto> resultado) {
    this.resultadoBusqueda = resultado;
  }

  void devolverEnBusquedaAdmin(ProductosPaginados resultado) {
    this.resultadoAdmin = resultado;
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

  @Override
  public Optional<Producto> buscarPorVarianteId(UUID varianteId) {
    return productos.stream()
        .filter(producto -> producto.variantes().stream().anyMatch(v -> v.id().equals(varianteId)))
        .findFirst();
  }

  @Override
  public ProductosPaginados buscarParaAdmin(int pagina, int tamanoPagina) {
    this.ultimaPaginaAdmin = pagina;
    this.ultimoTamanoPaginaAdmin = tamanoPagina;
    return resultadoAdmin;
  }

  @Override
  public void guardar(Producto producto) {
    this.ultimoGuardado = producto;
    this.productos = new ArrayList<>(productos);
    this.productos.add(producto);
  }

  @Override
  public Optional<Producto> buscarPorId(UUID id) {
    return productos.stream().filter(producto -> producto.id().equals(id)).findFirst();
  }

  @Override
  public void actualizar(Producto producto) {
    this.ultimoActualizado = producto;
  }
}
