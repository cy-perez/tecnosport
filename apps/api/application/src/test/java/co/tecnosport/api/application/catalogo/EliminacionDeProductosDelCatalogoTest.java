package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.EliminacionDeProductosDelCatalogo;
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

/**
 * En el paquete del catálogo y no en el de proveedores: así usa los dobles de {@code
 * EliminarProductoTest} y prueba la traducción contra el caso de uso de verdad.
 */
class EliminacionDeProductosDelCatalogoTest {

  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();
  private final RepositorioPedidosParaBorradoFalso pedidos =
      new RepositorioPedidosParaBorradoFalso();
  private final EliminacionDeProductosDelCatalogo eliminacion =
      new EliminacionDeProductosDelCatalogo(
          new EliminarProducto(productos, pedidos, new AlmacenDeImagenesFalso()));

  @Test
  void unBorradorSinVentasSeBorra() {
    Producto producto = conVariante();
    productos.conProductos(producto);

    assertTrue(eliminacion.eliminarSiSePuede(producto.id()));
    assertEquals(List.of(producto.id()), productos.productosEliminados);
  }

  @Test
  void unPublicadoSeQueda() {
    Producto producto = conVariante();
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://x/0.jpg", 1000)),
            null,
            600,
            new HashContenido("%064x".formatted(0)),
            "alt es",
            "alt en"));
    producto.publicar();
    productos.conProductos(producto);

    assertFalse(eliminacion.eliminarSiSePuede(producto.id()));
    assertTrue(productos.productosEliminados.isEmpty());
  }

  @Test
  void unoConVentasSeQueda() {
    Producto producto = conVariante();
    productos.conProductos(producto);
    pedidos.conVarianteVendida(producto.variantes().getFirst().id());

    assertFalse(eliminacion.eliminarSiSePuede(producto.id()));
    assertTrue(productos.productosEliminados.isEmpty());
  }

  @Test
  void unoQueYaNoExisteCuentaComoBorrado() {
    assertTrue(eliminacion.eliminarSiSePuede(UUID.randomUUID()));
  }

  private static Producto conVariante() {
    Producto producto =
        Producto.crear(
            "Bolso",
            new Slug("bolso"),
            "",
            Marca.crear("Genérica"),
            Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS));
    producto.agregarVariante(
        Variante.crear(
            new Sku("PRV-1"), Dinero.deCop(60000), BigDecimal.ZERO, null, null, List.of()));
    return producto;
  }
}
