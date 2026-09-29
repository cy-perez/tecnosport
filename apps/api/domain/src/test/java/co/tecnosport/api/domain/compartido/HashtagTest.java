package co.tecnosport.api.domain.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HashtagTest {

  @Test
  void laAlmohadillaSePoneSolaSiFalta() {
    assertEquals("#Parlantes", new Hashtag("Parlantes").valor());
  }

  @Test
  void siYaVieneConAlmohadillaNoSeDuplica() {
    assertEquals("#Parlantes", new Hashtag("#Parlantes").valor());
  }

  @Test
  void losEspaciosDeLosLadosNoCuentan() {
    assertEquals("#JBL", new Hashtag("  #JBL  ").valor());
  }

  /** {@code #Medellín} es una etiqueta real; quitarle la tilde la convertiría en otra distinta. */
  @Test
  void lasTildesYLasEnesSonEtiquetasValidas() {
    assertEquals("#Medellín", new Hashtag("Medellín").valor());
    assertEquals("#Niñas", new Hashtag("#Niñas").valor());
  }

  @Test
  void losDigitosYElGuionBajoCaben() {
    assertEquals("#Ropa_Deportiva2026", new Hashtag("Ropa_Deportiva2026").valor());
  }

  /**
   * El caso que motiva el objeto de valor: las redes cortan la etiqueta en el primer carácter que
   * no sea letra, dígito o guión bajo, así que {@code #ropa-dama} se publicaría como {@code #ropa}
   * y el resto quedaría de texto suelto. Mejor fallar aquí que en silencio dentro de un post.
   */
  @Test
  void unGuionPartiriaLaEtiquetaAlPublicarla() {
    ExcepcionDeDominio error =
        assertThrows(ExcepcionDeDominio.class, () -> new Hashtag("ropa-dama"));

    assertTrue(error.getMessage().contains("partiría"), error.getMessage());
  }

  @Test
  void unEspacioEnMedioTampocoVale() {
    assertThrows(ExcepcionDeDominio.class, () -> new Hashtag("#ropa dama"));
  }

  @Test
  void niUnPuntoNiUnaAlmohadillaEnMedio() {
    assertThrows(ExcepcionDeDominio.class, () -> new Hashtag("#ropa.dama"));
    assertThrows(ExcepcionDeDominio.class, () -> new Hashtag("#ropa#dama"));
  }

  @Test
  void unaEtiquetaVaciaNoEsUnaEtiqueta() {
    assertThrows(ExcepcionDeDominio.class, () -> new Hashtag("   "));
    assertThrows(ExcepcionDeDominio.class, () -> new Hashtag(null));
    // Solo la almohadilla tampoco: queda sin nada detrás.
    assertThrows(ExcepcionDeDominio.class, () -> new Hashtag("#"));
  }

  @Test
  void unaEtiquetaDesmesuradaNoCabe() {
    String larga = "a".repeat(Hashtag.MAXIMO_CARACTERES);

    ExcepcionDeDominio error = assertThrows(ExcepcionDeDominio.class, () -> new Hashtag(larga));

    assertTrue(error.getMessage().contains("no puede pasar de"), error.getMessage());
  }
}
