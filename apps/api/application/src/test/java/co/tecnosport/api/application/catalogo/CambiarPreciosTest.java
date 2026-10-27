package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.CambioDePrecio;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El precio de contado, cambiado desde la edición del producto; el publicado sale de él. */
class CambiarPreciosTest {

  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();
  private final CambiarPrecios caso = new CambiarPrecios(productos, TarifasDePrueba.WOMPI);

  private final Producto producto =
      Producto.crear(
          "Moto G17",
          new Slug("moto-g17"),
          "",
          Marca.crear("Motorola"),
          Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA));

  private final Variante de128 = variante("MG17-128", 599_900);
  private final Variante de256 = variante("MG17-256", 699_900);

  CambiarPreciosTest() {
    producto.agregarVariante(de128);
    producto.agregarVariante(de256);
    productos.conProductos(producto);
  }

  /**
   * Graba solo las que cambiaron —la que ya tenía ese contado no se escribe— y el publicado lo
   * calcula el servidor: (579.000 + 833) / 0,968465 = 598.707,4 → 599.000.
   */
  @Test
  void grabaLosPreciosNuevosSoloDeLasQueCambiaron() {
    List<CambioDePrecio> cambios =
        caso.ejecutar(
            new CambiarPreciosComando(
                producto.id(),
                Map.of(de128.id(), Dinero.deCop(579_000), de256.id(), Dinero.deCop(699_900))));

    assertEquals(
        List.of(
            new CambioDePrecio(
                de128.id(),
                new Sku("MG17-128"),
                Dinero.deCop(599_900),
                Dinero.deCop(579_000),
                Dinero.deCop(599_900),
                Dinero.deCop(599_000))),
        cambios);
    assertEquals(Map.of(de128.id(), Dinero.deCop(579_000)), productos.contadosGrabados);
    assertEquals(Map.of(de128.id(), Dinero.deCop(599_000)), productos.preciosGrabados);
  }

  @Test
  void unPrecioEnCeroNoGrabaNinguno() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            caso.ejecutar(
                new CambiarPreciosComando(
                    producto.id(),
                    Map.of(de128.id(), Dinero.deCop(579_000), de256.id(), Dinero.deCop(0)))));
    assertTrue(productos.preciosGrabados.isEmpty());
  }

  @Test
  void unProductoQueNoExisteSeDiceAsi() {
    UUID otro = UUID.randomUUID();

    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () -> caso.ejecutar(new CambiarPreciosComando(otro, Map.of())));
  }

  private static Variante variante(String sku, long precio) {
    return Variante.crear(
        new Sku(sku), Dinero.deCop(precio), BigDecimal.ZERO, null, null, List.of());
  }
}
