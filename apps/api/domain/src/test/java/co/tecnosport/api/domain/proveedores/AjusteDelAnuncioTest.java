package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Los anuncios de Meraki e Imperio Wicho del 7 al 13 de octubre de 2026, tal como llegaron. */
class AjusteDelAnuncioTest {

  private static Optional<AjusteDeTalla> de(int tallas) {
    return Optional.of(new AjusteDeTalla(tallas));
  }

  @Test
  void laHormaReducidaDeMerakiEsUnaTalla() {
    String polo =
        """
        *NUEVA POLO 1.1🍯*
         *MARCA P U M A FERRARI 🏎️*
        ⊷ *Ojo Horma reducida pedir una talla más*\s
        ⊷Tallas M-L-XL-XXL
        """;
    assertEquals(de(1), AjusteDelAnuncio.de(polo));
  }

  @Test
  void aumentarNTallasEsN() {
    assertEquals(
        de(2),
        AjusteDelAnuncio.de(
            "IMPORTADO CABALLERO👟👟\nTallas disponibles 40 a la 45\nAumentar 2 tallas⚠️⚠️"));
    assertEquals(
        de(1),
        AjusteDelAnuncio.de(
            "Importado Runking Caballero 🔥🔥\nTallas disponibles 39 a la 43!\nAumentar 1 talla⚠️⚠️"));
    assertEquals(
        de(2), AjusteDelAnuncio.de("Tallas disponibles 39 a la 43\nAumentar 2 tallas ⚠️⚠️"));
    assertEquals(de(2), AjusteDelAnuncio.de("aumenta dos tallas"));
  }

  @Test
  void unaTallaEurEsUnaMas() {
    assertEquals(
        de(1),
        AjusteDelAnuncio.de(
            "FOR ONE IMPORTADO AAA🔥✅✅\nTraen Accesorios tula y llavero⚠️\nTallas disponibles 36 a la 45 eur"));
    assertEquals(
        de(1), AjusteDelAnuncio.de("ADIDAS IMPORTADO AAA🔥\nTallas disponibles 41 la 44 eur"));
    assertEquals(de(1), AjusteDelAnuncio.de("Tallas disponibles 36 a la 39 EUR"));
  }

  @Test
  void eurYHormaReducidaSeSuman() {
    assertEquals(de(2), AjusteDelAnuncio.de("Tallas 36 a la 45 eur\nAumentar 1 talla"));
  }

  @Test
  void laHormaNormalNoCorreNada() {
    assertEquals(
        Optional.empty(),
        AjusteDelAnuncio.de(
            "Colección Caballero 🔥🔥\nImportado, Eva y Cosidos 📦\nTallas disponibles 38 a la 43!\nHorma normal⚠️⚠️"));
    assertEquals(Optional.empty(), AjusteDelAnuncio.de("Bolso de dama mediano 💰 53.000"));
    assertEquals(Optional.empty(), AjusteDelAnuncio.de(null));
  }

  /** El ajuste es solo para caballero: lo resuelve partir el anuncio, no correrle todo. */
  @Test
  void elAjusteSoloParaCaballeroNoEsDeAqui() {
    assertEquals(
        Optional.empty(),
        AjusteDelAnuncio.de(
            "NIKE UNISEX ULTRALIVIANO🔥\nTallas disponibles en la foto!\nHorma normal Dama, Aumenta 1 Cab⚠️⚠️"));
    assertEquals(Optional.empty(), AjusteDelAnuncio.de("Horma normal Dama, aumentar 1 Cab⚠️⚠️"));
  }

  /** «Europa», «euros» o un precio no son tallas EUR. */
  @Test
  void eurSueltoNoEsUnaTallaEur() {
    assertEquals(Optional.empty(), AjusteDelAnuncio.de("Calidad europea, 20 euros menos"));
  }

  @Test
  void masDeCuatroNoEsUnaHorma() {
    assertEquals(Optional.empty(), AjusteDelAnuncio.de("Aumentar 5 tallas"));
  }
}
