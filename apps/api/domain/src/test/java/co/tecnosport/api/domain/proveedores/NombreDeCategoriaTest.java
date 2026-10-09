package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NombreDeCategoriaTest {

  /** La Riverah, 8 de octubre de 2026: «Busito Manga larga Americano». */
  @Test
  void elDiminutivoSeEscribeConElNombreDeSuCategoria() {
    assertEquals("Buzo manga larga", NombreDeCategoria.corregir("Busito manga larga"));
    assertEquals("Camiseta slim", NombreDeCategoria.corregir("Camisetica slim"));
    assertEquals("Pantalón jogger", NombreDeCategoria.corregir("Pantaloncito jogger"));
    assertEquals("Bodi de copa", NombreDeCategoria.corregir("Bodicito de copa"));
  }

  /** La categoría del catálogo es «Buzos»: el «BUSO NAVIDEÑO» de Meraki también se corrige. */
  @Test
  void busoSeEscribeComoLaCategoria() {
    assertEquals("BUZO NAVIDEÑO", NombreDeCategoria.corregir("BUSO NAVIDEÑO"));
    assertEquals("Buzo navideño", NombreDeCategoria.corregir("Buso navideño"));
  }

  @Test
  void conservaElPluralYLaMayusculaDeLaPalabra() {
    assertEquals("Shorts y blusas", NombreDeCategoria.corregir("Shortcitos y blusitas"));
    assertEquals(
        "Un buzo cómodo con pantalones a juego",
        NombreDeCategoria.corregir("Un busito cómodo con pantaloncitos a juego"));
    assertEquals("BUZOS", NombreDeCategoria.corregir("BUSITOS"));
  }

  /**
   * Solo la palabra entera de la lista: «bonito» o «Busitos» dentro de otra palabra no se tocan.
   */
  @Test
  void loQueNoEstaEnLaListaNoSeToca() {
    assertEquals("Bolso bonito", NombreDeCategoria.corregir("Bolsito bonito"));
    assertEquals("Superbusito", NombreDeCategoria.corregir("Superbusito"));
    assertEquals("Buzo de hilo", NombreDeCategoria.corregir("Buzo de hilo"));
    assertNull(NombreDeCategoria.corregir(null));
  }

  @Test
  void elTituloYLaDescripcionDelExtraidoSalenCorregidos() {
    ProductoExtraido extraido =
        new ProductoExtraido(
            true,
            false,
            "Busito manga larga americano",
            LineaCatalogo.ROPA,
            TipoProductoProveedor.BUSO,
            Dinero.deCop(58000),
            Tallas.desconocida(),
            null,
            List.of(),
            null,
            "Busito de manga larga en tela licrada.",
            null,
            false,
            new BigDecimal("0.9"),
            null);

    assertEquals(Optional.of("Buzo manga larga americano"), extraido.tituloOpcional());
    assertEquals("Buzo de manga larga en tela licrada.", extraido.descripcion());
  }
}
