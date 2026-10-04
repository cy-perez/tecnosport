package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.Optional;
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
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA);
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

  /** La descripción es la de la ficha: editar no la deja vacía, como aprobar un borrador. */
  @Test
  void sinDescripcionNoSeEdita() {
    Marca marca = Marca.crear("Genérica");
    Categoria bodis = Categoria.crear("Bodis", new Slug("bodis-sd"), LineaCatalogo.ROPA);
    Producto bodi = Producto.crear("Bodi", new Slug("bodi-sd"), "Bodi.", marca, bodis);
    repositorioProductos.conProductos(bodi);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(bodis);

    assertThrows(
        ProductoSinDescripcionException.class,
        () ->
            editarProducto.ejecutar(
                new EditarProductoComando(bodi.id(), "Bodi", "  ", marca.id(), bodis.id())));
    assertEquals("Bodi.", bodi.descripcion());
  }

  /** El «sirve hasta» de una talla única: nulo no lo toca, en blanco lo quita. */
  @Test
  void elSirveHastaSeEditaSeConservaYSeQuita() {
    Marca marca = Marca.crear("Genérica");
    Categoria bodis = Categoria.crear("Bodis", new Slug("bodis"), LineaCatalogo.ROPA);
    Producto bodi = Producto.crear("Bodi herraje", new Slug("bodi"), "Bodi.", marca, bodis);
    repositorioProductos.conProductos(bodi);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(bodis);

    editarProducto.ejecutar(
        new EditarProductoComando(bodi.id(), "Bodi herraje", "Bodi.", marca.id(), bodis.id(), "L"));
    assertEquals(Optional.of("L"), bodi.tallaSirveHasta());

    editarProducto.ejecutar(
        new EditarProductoComando(bodi.id(), "Bodi herraje", "Bodi.", marca.id(), bodis.id()));
    assertEquals(Optional.of("L"), bodi.tallaSirveHasta(), "sin el campo no se toca");

    editarProducto.ejecutar(
        new EditarProductoComando(bodi.id(), "Bodi herraje", "Bodi.", marca.id(), bodis.id(), ""));
    assertEquals(Optional.empty(), bodi.tallaSirveHasta());
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

  /** Si las fotos generales acompañan a cada color: nulo no lo toca. */
  @Test
  void lasFotosGeneralesSeApaganYSeConservan() {
    Marca marca = Marca.crear("Genérica");
    Categoria bolsos = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    Producto bolso = Producto.crear("Bolso", new Slug("bolso"), "Bolso.", marca, bolsos);
    repositorioProductos.conProductos(bolso);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(bolsos);
    assertTrue(bolso.fotosGeneralesEnCadaColor(), "por omisión, como antes");

    editarProducto.ejecutar(
        new EditarProductoComando(
            bolso.id(), "Bolso", "Bolso.", marca.id(), bolsos.id(), null, false));
    assertFalse(bolso.fotosGeneralesEnCadaColor());

    editarProducto.ejecutar(
        new EditarProductoComando(bolso.id(), "Bolso", "Bolso.", marca.id(), bolsos.id()));
    assertFalse(bolso.fotosGeneralesEnCadaColor(), "sin el campo no se toca");
  }
}
