package co.tecnosport.api.domain.compartido;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SlugTest {

  @Test
  void aceptaKebabCase() {
    assertDoesNotThrow(() -> new Slug("camiseta-running-dry-fit"));
  }

  @Test
  void rechazaMayusculas() {
    assertThrows(SlugInvalidoException.class, () -> new Slug("Camiseta-Running"));
  }

  @Test
  void rechazaEspacios() {
    assertThrows(SlugInvalidoException.class, () -> new Slug("camiseta running"));
  }

  @Test
  void rechazaGuionesDobles() {
    assertThrows(SlugInvalidoException.class, () -> new Slug("camiseta--running"));
  }

  @Test
  void rechazaVacio() {
    assertThrows(SlugInvalidoException.class, () -> new Slug(""));
  }

  @Test
  void generarDesdeQuitaTildesYPasaAMinusculas() {
    assertEquals("camiseta-running", Slug.generarDesde("Camiseta Running").valor());
  }

  @Test
  void generarDesdeColapsaSimbolosEnUnGuion() {
    assertEquals("iphone-15-pro-max", Slug.generarDesde("iPhone 15, Pro Max!").valor());
  }

  @Test
  void generarDesdeQuitaGuionesAlPrincipioYAlFinal() {
    assertEquals("medias-deportivas", Slug.generarDesde("¡Medias deportivas!").valor());
  }

  @Test
  void generarDesdeConTextoSoloDeSimbolosLanzaSlugInvalido() {
    assertThrows(SlugInvalidoException.class, () -> Slug.generarDesde("¡¡¡!!!"));
  }
}
