package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrearProductoTest {

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final RepositorioMarcasFalso repositorioMarcas = new RepositorioMarcasFalso();
  private final RepositorioCategoriasFalso repositorioCategorias = new RepositorioCategoriasFalso();
  private final CrearProducto crearProducto =
      new CrearProducto(repositorioProductos, repositorioMarcas, repositorioCategorias);

  @Test
  void creaElProductoEnBorradorConSlugDerivadoDelNombre() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(categoria);

    Producto producto =
        crearProducto.ejecutar(
            new CrearProductoComando("Morral Urbano", "Descripción", marca.id(), categoria.id()));

    assertEquals("morral-urbano", producto.slug().valor());
    assertEquals(EstadoProducto.BORRADOR, producto.estado());
    assertEquals(producto, repositorioProductos.ultimoGuardado);
  }

  @Test
  void siElSlugYaExisteLeAgregaUnSufijoNumerico() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(categoria);
    repositorioProductos.conProductos(
        Producto.crear("Morral Urbano", new Slug("morral-urbano"), "", marca, categoria));

    Producto producto =
        crearProducto.ejecutar(
            new CrearProductoComando("Morral Urbano", "", marca.id(), categoria.id()));

    assertEquals("morral-urbano-2", producto.slug().valor());
  }

  @Test
  void marcaInexistenteLanzaMarcaNoEncontrada() {
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioCategorias.conCategorias(categoria);
    UUID marcaId = UUID.randomUUID();

    assertThrows(
        MarcaNoEncontradaException.class,
        () ->
            crearProducto.ejecutar(
                new CrearProductoComando("Morral Urbano", "", marcaId, categoria.id())));
  }

  @Test
  void categoriaInexistenteLanzaCategoriaNoEncontrada() {
    Marca marca = Marca.crear("TecnoSport");
    repositorioMarcas.conMarcas(marca);
    UUID categoriaId = UUID.randomUUID();

    assertThrows(
        CategoriaNoEncontradaException.class,
        () ->
            crearProducto.ejecutar(
                new CrearProductoComando("Morral Urbano", "", marca.id(), categoriaId)));
  }
}
