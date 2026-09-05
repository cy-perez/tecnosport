package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.Objects;

/**
 * Alta de un producto "pelado" desde el panel admin: sin variantes ni imágenes, en {@code BORRADOR}
 * — {@link Producto#crear} ya fuerza ese estado. El slug no lo escribe el admin: se deriva del
 * nombre ({@link Slug#generarDesde}) y, si choca con uno existente, se le agrega un sufijo numérico
 * hasta encontrar uno libre.
 */
public final class CrearProducto {

  private final RepositorioProductos repositorioProductos;
  private final RepositorioMarcas repositorioMarcas;
  private final RepositorioCategorias repositorioCategorias;

  public CrearProducto(
      RepositorioProductos repositorioProductos,
      RepositorioMarcas repositorioMarcas,
      RepositorioCategorias repositorioCategorias) {
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioMarcas = Objects.requireNonNull(repositorioMarcas);
    this.repositorioCategorias = Objects.requireNonNull(repositorioCategorias);
  }

  public Producto ejecutar(CrearProductoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Marca marca =
        repositorioMarcas
            .buscarPorId(comando.marcaId())
            .orElseThrow(() -> new MarcaNoEncontradaException(comando.marcaId()));
    Categoria categoria =
        repositorioCategorias
            .buscarPorId(comando.categoriaId())
            .orElseThrow(() -> new CategoriaNoEncontradaException(comando.categoriaId()));

    Slug slug = slugDisponible(Slug.generarDesde(comando.nombre()));

    Producto producto =
        Producto.crear(comando.nombre(), slug, comando.descripcion(), marca, categoria);
    repositorioProductos.guardar(producto);
    return producto;
  }

  private Slug slugDisponible(Slug candidato) {
    Slug intento = candidato;
    int sufijo = 2;
    while (repositorioProductos.buscarPorSlug(intento).isPresent()) {
      intento = new Slug(candidato.valor() + "-" + sufijo);
      sufijo++;
    }
    return intento;
  }
}
