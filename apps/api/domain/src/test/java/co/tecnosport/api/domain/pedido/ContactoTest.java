package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import org.junit.jupiter.api.Test;

class ContactoTest {

  @Test
  void normalizaElTelefonoADigitosYRecortaElNombre() {
    Contacto contacto = new Contacto("  Ana Pérez ", "(313) 881-6711");

    assertEquals("Ana Pérez", contacto.nombre());
    assertEquals("3138816711", contacto.telefono());
  }

  @Test
  void conservaElPrefijoInternacional() {
    assertEquals("+573138816711", new Contacto("Ana", "+57 313 881 6711").telefono());
  }

  @Test
  void rechazaNombreVacio() {
    assertThrows(ExcepcionDeDominio.class, () -> new Contacto("  ", "3138816711"));
  }

  @Test
  void rechazaTelefonoConLetras() {
    assertThrows(ExcepcionDeDominio.class, () -> new Contacto("Ana", "313 ABC 6711"));
  }

  @Test
  void rechazaTelefonoDemasiadoCorto() {
    assertThrows(ExcepcionDeDominio.class, () -> new Contacto("Ana", "12345"));
  }

  @Test
  void rechazaTelefonoVacio() {
    assertThrows(ExcepcionDeDominio.class, () -> new Contacto("Ana", ""));
  }
}
