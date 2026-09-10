package co.tecnosport.api.application.garantia;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Solo resuelve {@code buscarPorVarianteId}, que es lo unico que la garantia necesita del catalogo:
 * la categoria del producto, de la que sale el termino. El resto lanza, para que una prueba que se
 * apoye en algo no previsto falle en vez de pasar por casualidad.
 */
final class RepositorioProductosParaGarantiaFalso implements RepositorioProductos {

  private final Map<UUID, Producto> porVariante = new HashMap<>();

  void conProducto(UUID varianteId, Producto producto) {
    porVariante.put(varianteId, producto);
  }

  @Override
  public Optional<Producto> buscarPorVarianteId(UUID varianteId) {
    return Optional.ofNullable(porVariante.get(varianteId));
  }

  @Override
  public ResultadoPaginado<Producto> buscar(
      FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina) {
    throw new UnsupportedOperationException("no lo usa la garantia");
  }

  @Override
  public Optional<Producto> buscarPorSlug(Slug slug) {
    throw new UnsupportedOperationException("no lo usa la garantia");
  }

  @Override
  public ProductosPaginados buscarParaAdmin(int pagina, int tamanoPagina) {
    throw new UnsupportedOperationException("no lo usa la garantia");
  }

  @Override
  public void guardar(Producto producto) {
    throw new UnsupportedOperationException("no lo usa la garantia");
  }

  @Override
  public Optional<Producto> buscarPorId(UUID id) {
    throw new UnsupportedOperationException("no lo usa la garantia");
  }

  @Override
  public void actualizar(Producto producto) {
    throw new UnsupportedOperationException("no lo usa la garantia");
  }

  @Override
  public void agregarVariante(UUID productoId, Variante variante) {
    throw new UnsupportedOperationException("no lo usa la garantia");
  }

  @Override
  public boolean existeVarianteConSku(Sku sku) {
    throw new UnsupportedOperationException("no lo usa la garantia");
  }

  @Override
  public void guardarImagenPrincipal(UUID productoId, ImagenProducto imagen) {
    throw new UnsupportedOperationException("no lo usa la garantia");
  }

  static List<UUID> vacio() {
    return List.of();
  }
}
