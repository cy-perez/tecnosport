package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EliminarProductoTest {

  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();
  private final RepositorioPedidosParaBorradoFalso pedidos =
      new RepositorioPedidosParaBorradoFalso();
  private final AlmacenDeImagenesFalso almacen = new AlmacenDeImagenesFalso();
  private final EliminarProducto eliminar = new EliminarProducto(productos, pedidos, almacen);

  @Test
  void borraElProductoYDevuelveCuantosObjetosLimpioDelBucket() {
    Producto producto = borradorConVariante("SKU-1");
    productos.conProductos(producto);
    almacen.conObjeto("productos/" + producto.id() + "/principal-a.jpg", 100);
    almacen.conObjeto("productos/" + producto.id() + "/galeria-b.jpg", 100);
    almacen.conObjeto("productos/" + producto.id() + "/rotacion/set-1/0.jpg", 100);

    int borrados = eliminar.ejecutar(producto.id());

    assertEquals(3, borrados);
    assertEquals(List.of(producto.id()), productos.productosEliminados);
  }

  /**
   * Un solo prefijo para las tres clases de imagen. Si alguien lo estrecha a {@code principal-} o
   * recorre los sets de rotación uno por uno, la galería o los fotogramas se quedan en el bucket
   * pagándose para siempre, y nada más lo diría.
   */
  @Test
  void limpiaElProductoEnteroDelBucket() {
    Producto producto = borradorConVariante("SKU-1");
    productos.conProductos(producto);

    eliminar.ejecutar(producto.id());

    assertEquals(List.of("productos/" + producto.id() + "/"), almacen.prefijosEliminados);
  }

  /** No se borra lo que está en la vitrina: primero se retira. */
  @Test
  void unProductoPublicadoNoSeBorra() {
    Producto producto = borradorConVariante("SKU-1");
    producto.asignarImagenPrincipal(imagenPrincipal());
    producto.publicar();
    productos.conProductos(producto);

    assertThrows(ProductoPublicadoException.class, () -> eliminar.ejecutar(producto.id()));
    assertTrue(productos.productosEliminados.isEmpty());
    assertTrue(almacen.prefijosEliminados.isEmpty());
  }

  /**
   * El rechazo que de verdad protege algo: la garantía y el retracto buscan el producto a partir
   * del id de la variante vendida, y ese vínculo no lo sostiene ninguna llave foránea.
   */
  @Test
  void unProductoConVentasNoSeBorraNiSiquieraEnBorrador() {
    Producto producto = borradorConVariante("SKU-1");
    productos.conProductos(producto);
    pedidos.conVarianteVendida(producto.variantes().getFirst().id());

    assertThrows(ProductoConVentasException.class, () -> eliminar.ejecutar(producto.id()));
    assertTrue(productos.productosEliminados.isEmpty());
    assertTrue(almacen.prefijosEliminados.isEmpty());
  }

  /**
   * Se pregunta por **todas** las variantes en una sola consulta. Con la venta en la segunda talla
   * de un producto de dos, preguntar solo por la primera dejaría borrar un producto vendido.
   */
  @Test
  void preguntaPorTodasLasVariantesDeUnaVez() {
    Producto producto = borradorConVariante("SKU-1");
    Variante segunda = variante("SKU-2");
    producto.agregarVariante(segunda);
    productos.conProductos(producto);
    pedidos.conVarianteVendida(segunda.id());

    assertThrows(ProductoConVentasException.class, () -> eliminar.ejecutar(producto.id()));
    assertEquals(1, pedidos.consultas.size());
    assertEquals(2, pedidos.consultas.getFirst().size());
  }

  /**
   * Un producto recién creado no tiene variantes, y ahí no hay a quién preguntarle: un {@code in
   * ()} vacío no es SQL válido, así que el caso de uso no puede delegar la decisión al adaptador.
   */
  @Test
  void unProductoSinVariantesNiPreguntaPorVentas() {
    Producto producto = borrador();
    productos.conProductos(producto);

    eliminar.ejecutar(producto.id());

    assertTrue(pedidos.consultas.isEmpty());
    assertEquals(List.of(producto.id()), productos.productosEliminados);
  }

  @Test
  void unIdQueNoExisteLoDice() {
    assertThrows(
        ProductoNoEncontradoPorIdException.class, () -> eliminar.ejecutar(UUID.randomUUID()));
  }

  private static Producto borrador() {
    Marca marca = Marca.crear("JBL");
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);
    return Producto.crear("JBL Charge 6", new Slug("jbl-charge-6"), "", marca, categoria);
  }

  private static Producto borradorConVariante(String sku) {
    Producto producto = borrador();
    producto.agregarVariante(variante(sku));
    return producto;
  }

  private static Variante variante(String sku) {
    return Variante.crear(
        new Sku(sku), Dinero.deCop(890_000), new BigDecimal("0.00"), null, null, List.of());
  }

  private static ImagenProducto imagenPrincipal() {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        List.of(new VarianteDeImagen(2000, "https://x/0.jpg", 1000)),
        null,
        2000,
        new HashContenido("%064x".formatted(0)),
        "alt es",
        "alt en");
  }
}
