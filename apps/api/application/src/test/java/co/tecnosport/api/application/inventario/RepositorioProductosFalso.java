package co.tecnosport.api.application.inventario;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.MedidaDeVariante;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioProductosFalso implements RepositorioProductos {

  private List<Producto> productos = new ArrayList<>();
  private List<VarianteActiva> activas = List.of();

  void conProductos(Producto... productos) {
    this.productos = List.of(productos);
  }

  void conVariantesActivas(VarianteActiva... variantes) {
    this.activas = List.of(variantes);
  }

  @Override
  public Optional<Producto> buscarPorVarianteId(UUID varianteId) {
    return productos.stream()
        .filter(
            producto ->
                producto.variantes().stream()
                    .anyMatch(variante -> variante.id().equals(varianteId)))
        .findFirst();
  }

  @Override
  public List<VarianteActiva> variantesActivas() {
    return activas;
  }

  @Override
  public ResultadoPaginado<Producto> buscar(
      FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina) {
    return new ResultadoPaginado<>(List.of(), null);
  }

  @Override
  public Optional<Producto> buscarPorSlug(Slug slug) {
    return Optional.empty();
  }

  @Override
  public ProductosPaginados buscarParaAdmin(int pagina, int tamanoPagina) {
    return new ProductosPaginados(List.of(), 0, 0, 0);
  }

  @Override
  public void guardar(Producto producto) {}

  @Override
  public Optional<Producto> buscarPorId(UUID id) {
    return productos.stream().filter(producto -> producto.id().equals(id)).findFirst();
  }

  @Override
  public void actualizar(Producto producto) {}

  @Override
  public void agregarVariante(UUID productoId, Variante variante) {}

  @Override
  public boolean existeVarianteConSku(Sku sku) {
    return false;
  }

  @Override
  public List<MedidaDeVariante> medidasDeVariantes() {
    return List.of();
  }

  @Override
  public void actualizarPaquete(UUID varianteId, Paquete paquete) {}

  @Override
  public void guardarImagenPrincipal(UUID productoId, ImagenProducto imagen) {}

  @Override
  public void guardarImagenDeGaleria(UUID productoId, ImagenProducto imagen) {}

  @Override
  public void eliminarImagenDeGaleria(UUID productoId, UUID imagenId) {}

  @Override
  public void guardarOrdenDeGaleria(UUID productoId, List<ImagenProducto> galeria) {}
}
