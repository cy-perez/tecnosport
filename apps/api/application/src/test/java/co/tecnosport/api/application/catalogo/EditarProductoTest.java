package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditarProductoTest {

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final RepositorioMarcasFalso repositorioMarcas = new RepositorioMarcasFalso();
  private final RepositorioCategoriasFalso repositorioCategorias = new RepositorioCategoriasFalso();
  private final EditarProducto editarProducto =
      new EditarProducto(repositorioProductos, repositorioMarcas, repositorioCategorias);

  @Test
  void actualizaNombreDescripcionMarcaYCategoriaSinTocarElSlug() {
    Marca marcaOriginal = Marca.crear("TecnoSport");
    Categoria categoriaOriginal =
        Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    Producto producto =
        Producto.crear(
            "Morral urbano", new Slug("morral-urbano"), "", marcaOriginal, categoriaOriginal);
    repositorioProductos.conProductos(producto);
    Marca nuevaMarca = Marca.crear("Under Trail");
    Categoria nuevaCategoria =
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.CELULARES);
    repositorioMarcas.conMarcas(marcaOriginal, nuevaMarca);
    repositorioCategorias.conCategorias(categoriaOriginal, nuevaCategoria);

    Producto editado =
        editarProducto.ejecutar(
            new EditarProductoComando(
                producto.id(),
                "Morral urbano renovado",
                "Nueva descripción",
                nuevaMarca.id(),
                nuevaCategoria.id()));

    assertSame(producto, editado);
    assertEquals("Morral urbano renovado", editado.nombre());
    assertEquals("Nueva descripción", editado.descripcion());
    assertEquals(nuevaMarca, editado.marca());
    assertEquals(nuevaCategoria, editado.categoria());
    assertEquals("morral-urbano", editado.slug().valor());
    assertSame(producto, repositorioProductos.ultimoActualizado);
  }

  @Test
  void productoInexistenteLanzaProductoNoEncontradoPorId() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(categoria);
    UUID productoId = UUID.randomUUID();

    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            editarProducto.ejecutar(
                new EditarProductoComando(productoId, "Nombre", "", marca.id(), categoria.id())));
  }

  @Test
  void marcaInexistenteLanzaMarcaNoEncontrada() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    Producto producto =
        Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
    repositorioProductos.conProductos(producto);
    repositorioCategorias.conCategorias(categoria);
    UUID marcaId = UUID.randomUUID();

    assertThrows(
        MarcaNoEncontradaException.class,
        () ->
            editarProducto.ejecutar(
                new EditarProductoComando(producto.id(), "Nombre", "", marcaId, categoria.id())));
  }

  @Test
  void categoriaInexistenteLanzaCategoriaNoEncontrada() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    Producto producto =
        Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
    repositorioProductos.conProductos(producto);
    repositorioMarcas.conMarcas(marca);
    UUID categoriaId = UUID.randomUUID();

    assertThrows(
        CategoriaNoEncontradaException.class,
        () ->
            editarProducto.ejecutar(
                new EditarProductoComando(producto.id(), "Nombre", "", marca.id(), categoriaId)));
  }
}
