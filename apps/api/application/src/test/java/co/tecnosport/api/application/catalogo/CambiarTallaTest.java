package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TallaRepetidaException;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** La talla de un modelo, corregida desde la edición del producto. */
class CambiarTallaTest {

  private static final Atributo COLOR = Atributo.crear("Color", TipoAtributo.COLOR, List.of());
  private static final Atributo TALLA = Atributo.crear("Talla", TipoAtributo.TEXTO, List.of());

  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();
  private final CambiarTalla caso = new CambiarTalla(productos);

  private final Producto producto =
      Producto.crear(
          "Conjunto deportivo",
          new Slug("conjunto-deportivo"),
          "",
          Marca.crear("Genérica"),
          Categoria.crear("Conjuntos", new Slug("ropa-dama-conjuntos"), LineaCatalogo.ROPA));

  private final Variante negroS = variante("C-N-S", "S-M", "Negro");
  private final Variante negroL = variante("C-N-L", "L-XL", "Negro");
  private final Variante vinoS = variante("C-V-S", "S-M", "Vino");

  CambiarTallaTest() {
    producto.agregarVariante(negroS);
    producto.agregarVariante(negroL);
    producto.agregarVariante(vinoS);
    productos.conProductos(producto);
  }

  /** Graba la talla nueva en cada color del modelo, y en ninguna otra variante. */
  @Test
  void grabaLaTallaNuevaEnTodosLosColoresDelModelo() {
    List<Variante> cambiadas =
        caso.ejecutar(new CambiarTallaComando(producto.id(), vinoS.id(), "M"));

    assertEquals(List.of(negroS.id(), vinoS.id()), cambiadas.stream().map(Variante::id).toList());
    assertEquals(
        List.of(negroS.id(), vinoS.id()), List.copyOf(productos.atributosReemplazados.keySet()));
    for (ValorAtributo grabado : productos.atributosReemplazados.values()) {
      assertEquals(TALLA, grabado.atributo());
      assertEquals("M", grabado.valor());
    }
  }

  @Test
  void laMismaTallaNoGrabaNada() {
    assertEquals(
        List.of(), caso.ejecutar(new CambiarTallaComando(producto.id(), negroS.id(), "S-M")));
    assertTrue(productos.atributosReemplazados.isEmpty());
  }

  @Test
  void unaTallaQueYaExisteNoGrabaNada() {
    assertThrows(
        TallaRepetidaException.class,
        () -> caso.ejecutar(new CambiarTallaComando(producto.id(), negroS.id(), "L-XL")));
    assertTrue(productos.atributosReemplazados.isEmpty());
  }

  @Test
  void unProductoQueNoExisteEsUn404() {
    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () -> caso.ejecutar(new CambiarTallaComando(UUID.randomUUID(), negroS.id(), "M")));
  }

  private static Variante variante(String sku, String talla, String color) {
    return Variante.crear(
        new Sku(sku),
        Dinero.deCop(60_000),
        BigDecimal.ZERO,
        null,
        null,
        List.of(ValorAtributo.deColor(COLOR, color, "#111111"), ValorAtributo.de(TALLA, talla)));
  }
}
