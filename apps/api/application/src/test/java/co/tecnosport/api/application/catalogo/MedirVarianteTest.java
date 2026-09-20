package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MedirVarianteTest {

  private final RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
  private final MedirVariante medirVariante = new MedirVariante(repositorio);

  @Test
  void mideUnaVarianteQueEstabaSinMedirYLoGraba() {
    Variante variante = varianteSinMedir("TS-MOTO-G17");
    repositorio.conProductos(productoCon(variante));

    ResultadoDeMedicion resultado =
        medirVariante.ejecutar(new MedirVarianteComando(variante.id(), 430, 17, 9, 5));

    assertEquals(Optional.of(new Paquete(430, 17, 9, 5)), resultado.variante().paquete());
    assertFalse(resultado.correccion());
    assertEquals(variante.id(), repositorio.ultimaVarianteMedida);
    assertEquals(new Paquete(430, 17, 9, 5), repositorio.ultimoPaqueteGrabado);
  }

  /**
   * Remedir es legal y se declara como corrección. Lo segundo importa tanto como lo primero: es la
   * única señal de que algún pedido anterior salió con el flete calculado sobre la cifra vieja.
   */
  @Test
  void remedirReemplazaLaMedidaYSeDeclaraComoCorreccion() {
    Variante variante = varianteMedida("TS-JBL-GO5", new Paquete(300, 12, 8, 5));
    repositorio.conProductos(productoCon(variante));

    ResultadoDeMedicion resultado =
        medirVariante.ejecutar(new MedirVarianteComando(variante.id(), 420, 14, 9, 6));

    assertTrue(resultado.correccion());
    assertEquals(Optional.of(new Paquete(420, 14, 9, 6)), resultado.variante().paquete());
    assertEquals(new Paquete(420, 14, 9, 6), repositorio.ultimoPaqueteGrabado);
  }

  @Test
  void unaVarianteQueNoExisteNoSeMide() {
    UUID inexistente = UUID.randomUUID();

    VarianteNoEncontradaPorIdException excepcion =
        assertThrows(
            VarianteNoEncontradaPorIdException.class,
            () -> medirVariante.ejecutar(new MedirVarianteComando(inexistente, 430, 17, 9, 5)));

    assertTrue(excepcion.getMessage().contains(inexistente.toString()));
    assertNull(repositorio.ultimoPaqueteGrabado);
  }

  /**
   * Una medida en cero no se guarda, y no hace falta que este caso de uso lo compruebe: lo hace
   * {@link Paquete}. La prueba está para que no se cuele una validación paralela aquí que un día
   * diga otra cosa — y, sobre todo, para fijar que <b>no se escribe nada</b> cuando revienta.
   */
  @Test
  void unaMedidaEnCeroNoLlegaAGrabarse() {
    Variante variante = varianteSinMedir("TS-MOTO-G17");
    repositorio.conProductos(productoCon(variante));

    assertThrows(
        ExcepcionDeDominio.class,
        () -> medirVariante.ejecutar(new MedirVarianteComando(variante.id(), 430, 0, 9, 5)));

    assertNull(repositorio.ultimoPaqueteGrabado);
  }

  private static Producto productoCon(Variante variante) {
    Marca marca = Marca.crear("Motorola");
    Categoria categoria =
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA);
    Producto producto = Producto.crear("Moto G17", new Slug("moto-g17"), "", marca, categoria);
    producto.agregarVariante(variante);
    return producto;
  }

  private static Variante varianteSinMedir(String sku) {
    return varianteMedida(sku, null);
  }

  private static Variante varianteMedida(String sku, Paquete paquete) {
    return Variante.crear(
        new Sku(sku), Dinero.deCop(890_000), new BigDecimal("0.00"), 5, null, paquete, List.of());
  }
}
