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
    // Tenis Al Pie, 23 de octubre de 2026: la marca delante y abreviada.
    assertEquals(de(1), AjusteDelAnuncio.de("Tallas hombre \nEu 40 41 42 43 44"));
    assertEquals(de(1), AjusteDelAnuncio.de("Talla dama EUR 36 37 38 39"));
    // Imperio Wicho, 27 de octubre de 2026: la exclamación entre el rango y la marca.
    assertEquals(
        de(1),
        AjusteDelAnuncio.de(
            "Importado AAA Dama y Cab👸🏻👨🏻🔥\nTallas disponibles 36 a la 43! Eur"));
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

  /** Imperio Wicho, 10 de octubre de 2026, NIKE y ADIDAS UNISEX: dama igual, caballero una más. */
  @Test
  void elAnuncioUnisexDaElAjusteDeCadaGenero() {
    AjusteDelAnuncio.PorGenero esperado =
        new AjusteDelAnuncio.PorGenero(AjusteDeTalla.NINGUNO, new AjusteDeTalla(1));
    assertEquals(
        Optional.of(esperado),
        AjusteDelAnuncio.porGenero(
            "NIKE UNISEX ULTRALIVIANO🔥\nSUELA IMPORTADA💯\nTallas disponibles en la foto!\n"
                + "Horma normal Dama, Aumenta 1 Cab⚠️⚠️"));
    assertEquals(
        Optional.of(esperado), AjusteDelAnuncio.porGenero("Horma normal Dama, aumentar 1 Cab⚠️⚠️"));
  }

  /** Las dos partes nacen de tallas EUR: la de dama una, la de caballero dos. */
  @Test
  void elAnuncioUnisexEnTallasEurSumaEnLasDosPartes() {
    assertEquals(
        Optional.of(new AjusteDelAnuncio.PorGenero(new AjusteDeTalla(1), new AjusteDeTalla(2))),
        AjusteDelAnuncio.porGenero(
            "Tallas disponibles 36 a la 45 eur\nHorma normal Dama, Aumenta 1 Cab"));
  }

  @Test
  void elAnuncioUnisexSeLeeEscritoDeOtrasManeras() {
    Optional<AjusteDelAnuncio.PorGenero> uno =
        Optional.of(new AjusteDelAnuncio.PorGenero(AjusteDeTalla.NINGUNO, new AjusteDeTalla(1)));
    assertEquals(uno, AjusteDelAnuncio.porGenero("Horma normal Damas y aumenta 1 Caballeros"));
    assertEquals(uno, AjusteDelAnuncio.porGenero("Horma normal Dama,\naumentar 1Cab"));
    assertEquals(uno, AjusteDelAnuncio.porGenero("Horma\u00a0normal Dama, Aumenta\u00a01 Cab"));
  }

  @Test
  void unAnuncioDeUnSoloGeneroNoSeParte() {
    assertEquals(Optional.empty(), AjusteDelAnuncio.porGenero("Aumentar 2 tallas⚠️⚠️"));
    assertEquals(Optional.empty(), AjusteDelAnuncio.porGenero("Horma normal⚠️⚠️"));
    assertEquals(Optional.empty(), AjusteDelAnuncio.porGenero(null));
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
