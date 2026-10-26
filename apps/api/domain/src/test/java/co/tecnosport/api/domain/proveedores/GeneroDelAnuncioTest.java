package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Para quién es el tenis según el texto de Imperio Wicho (ADR-0092). */
class GeneroDelAnuncioTest {

  @Test
  void leeElGeneroQueEscribeElProveedor() {
    assertEquals(
        Optional.of(GeneroDeCalzado.CABALLERO),
        GeneroDelAnuncio.de("IMPORTADO CABALLERO👟👟\nTallas disponibles 40 a la 45"));
    assertEquals(Optional.of(GeneroDeCalzado.DAMA), GeneroDelAnuncio.de("Tenis para Dama 💕"));
    assertEquals(
        Optional.of(GeneroDeCalzado.UNISEX), GeneroDelAnuncio.de("NIKE UNISEX ULTRALIVIANO🔥"));
  }

  @Test
  void nombrarDamaYCaballeroEsUnisex() {
    assertEquals(
        Optional.of(GeneroDeCalzado.UNISEX),
        GeneroDelAnuncio.de("Horma normal Dama, Aumenta 1 Cab⚠️"));
  }

  @Test
  void sinGeneroEnElTextoNoAdivina() {
    assertEquals(Optional.empty(), GeneroDelAnuncio.de("Detal :$180.000 \nX mayor 💰135"));
    assertEquals(Optional.empty(), GeneroDelAnuncio.de("Cabello, cabina y madama no cuentan"));
    assertEquals(Optional.empty(), GeneroDelAnuncio.de(null));
  }
}
