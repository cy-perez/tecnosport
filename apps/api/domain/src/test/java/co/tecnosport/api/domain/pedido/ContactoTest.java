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

  /**
   * El nombre va impreso en la guía y es por el que el mensajero pregunta en la puerta, así que
   * "@#$%" no es un dato feo sino una entrega que no se puede hacer. El formulario web ya lo
   * rechaza; esto es lo que protege a la app móvil de la fase 2 y a cualquier otro cliente.
   */
  @Test
  void rechazaNombreConSimbolos() {
    assertThrows(ExcepcionDeDominio.class, () -> new Contacto("@#$%", "3138816711"));
    assertThrows(ExcepcionDeDominio.class, () -> new Contacto("<script>", "3138816711"));
  }

  @Test
  void rechazaNombreConDigitos() {
    assertThrows(ExcepcionDeDominio.class, () -> new Contacto("Ana 123", "3138816711"));
  }

  /** Una letra suelta no es un nombre: es lo que se escribe para saltarse un campo obligatorio. */
  @Test
  void rechazaNombreDeUnaSolaLetra() {
    assertThrows(ExcepcionDeDominio.class, () -> new Contacto("a", "3138816711"));
  }

  /** Lo que sí son nombres, y que una regla de "solo letras" a secas habría roto. */
  @Test
  void aceptaApostrofoGuionPuntoYTildes() {
    assertEquals("María D'Angelo", new Contacto("María D'Angelo", "3138816711").nombre());
    assertEquals("Ruiz-Mejía", new Contacto("Ruiz-Mejía", "3138816711").nombre());
    assertEquals("J. Gómez", new Contacto("J. Gómez", "3138816711").nombre());
    assertEquals("Ñungo Çelik", new Contacto("Ñungo Çelik", "3138816711").nombre());
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
