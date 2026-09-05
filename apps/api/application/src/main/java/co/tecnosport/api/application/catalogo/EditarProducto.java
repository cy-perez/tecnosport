package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import java.util.Objects;

/**
 * Edita nombre, descripción, marca y categoría de un producto existente. No toca variantes,
 * imágenes ni estado — {@link Producto#actualizarDatosBasicos} tampoco lo permite.
 */
public final class EditarProducto {

  private final RepositorioProductos repositorioProductos;
  private final RepositorioMarcas repositorioMarcas;
  private final RepositorioCategorias repositorioCategorias;

  public EditarProducto(
      RepositorioProductos repositorioProductos,
      RepositorioMarcas repositorioMarcas,
      RepositorioCategorias repositorioCategorias) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioMarcas = Objects.requireNonNull(repositorioMarcas);
    this.repositorioCategorias = Objects.requireNonNull(repositorioCategorias);
  }

  public Producto ejecutar(EditarProductoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Producto producto =
        repositorioProductos
            .buscarPorId(comando.productoId())
            .orElseThrow(() -> new ProductoNoEncontradoPorIdException(comando.productoId()));
    Marca marca =
        repositorioMarcas
            .buscarPorId(comando.marcaId())
            .orElseThrow(() -> new MarcaNoEncontradaException(comando.marcaId()));
    Categoria categoria =
        repositorioCategorias
            .buscarPorId(comando.categoriaId())
            .orElseThrow(() -> new CategoriaNoEncontradaException(comando.categoriaId()));

    producto.actualizarDatosBasicos(comando.nombre(), comando.descripcion(), marca, categoria);
    repositorioProductos.actualizar(producto);
    return producto;
  }
}
