package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextoDeAnuncioTest {

  /** La polo Prada de Meraki: la misma en el chat exportado del iPhone y en el de Android. */
  @Test
  void elMismoAnuncioDeIphoneYDeAndroidEsElMismo() {
    String iphone =
        "*NUEVA POLO 1.1🍯*\r\n *MARCA   P R A D A*\n*TELA FRIA*\n⊷Producto importado 1.1 \n"
            + "*PRECIO X DIFUSIÓN $50.000💰*";
    String android =
        "*NUEVA POLO 1.1🍯*\n *MARCA   P R A D A*\n*TELA FRIA*\n⊷Producto importado 1.1\n"
            + "*PRECIO X DIFUSION $50.000💰*";

    assertTrue(TextoDeAnuncio.mismoAnuncio(iphone, android));
  }

  @Test
  void normalizaMayusculasTildesEmojisYEspacios() {
    assertEquals(
        "BUSO NAVIDENO 45 000", TextoDeAnuncio.normalizar("*Buso   Navideño🎄*\n💰 $45.000"));
  }

  /** Otro precio u otra marca son otro anuncio; dos textos vacíos no son el mismo. */
  @Test
  void loQueDiceOtraCosaNoEsElMismoAnuncio() {
    assertFalse(TextoDeAnuncio.mismoAnuncio("Polo Prada $50.000", "Polo Prada $45.000"));
    assertFalse(TextoDeAnuncio.mismoAnuncio("Polo Prada", "Polo Hugo"));
    assertFalse(TextoDeAnuncio.mismoAnuncio("🎄🎄", "🎄"));
    assertFalse(TextoDeAnuncio.mismoAnuncio(null, null));
  }
}
