package co.tecnosport.api.domain.compartido;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
}
