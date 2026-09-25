package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.VarianteActiva;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class RepositorioProductosDobleDePrueba implements RepositorioProductos {

  private List<Producto> productos = List.of();

  void conProductos(Producto... productos) {
    this.productos = List.of(productos);
  }

  void limpiar() {
    this.productos = List.of();
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

  /**
   * <b>Este doble no reproduce el filtro del adaptador real.</b> {@code MapeadorCatalogo} descarta
   * las variantes que no están {@code ACTIVA} antes de construir el {@code Producto}; aquí se
   * devuelve el agregado tal como lo armó la prueba. O sea que una prueba que meta una variante
   * inactiva está montando un escenario que producción no produce, y lo que compruebe de ahí no
   * dice nada del comportamiento real. Ver {@code CrearPedido.buscarVarianteVendible}.
   */
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

  @Override
  public void agregarVariante(UUID productoId, Variante variante) {
    throw new UnsupportedOperationException("No usado por PedidoControladorTest.");
  }

  @Override
  public boolean existeVarianteConSku(Sku sku) {
    throw new UnsupportedOperationException("No usado por PedidoControladorTest.");
  }

  @Override
  public void guardarImagenPrincipal(UUID productoId, ImagenProducto imagen) {
    throw new UnsupportedOperationException("No usado por PedidoControladorTest.");
  }

  @Override
  public void guardarImagenDeGaleria(UUID productoId, ImagenProducto imagen) {
    throw new UnsupportedOperationException("Este doble no guarda imágenes.");
  }

  @Override
  public boolean eliminarImagenDeGaleria(UUID productoId, UUID imagenId) {
    throw new UnsupportedOperationException("Este doble no guarda imágenes.");
  }

  @Override
  public void guardarOrdenDeGaleria(UUID productoId, List<ImagenProducto> galeria) {
    throw new UnsupportedOperationException("No usado por los controladores de pedido.");
  }

  @Override
  public java.util.List<co.tecnosport.api.application.catalogo.MedidaDeVariante>
      medidasDeVariantes() {
    throw new UnsupportedOperationException("No usado por las pruebas de pedido.");
  }

  @Override
  public void actualizarPaquete(UUID varianteId, Paquete paquete) {
    throw new UnsupportedOperationException("No usado por las pruebas de pedido.");
  }

  /** No lo usa esta prueba: el listado de existencias tiene el suyo. */
  @Override
  public List<VarianteActiva> variantesActivas() {
    return List.of();
  }

  /** No lo usa esta prueba: ajustar existencia tiene la suya. */
  @Override
  public void eliminar(UUID productoId) {
    throw new UnsupportedOperationException();
  }
}
