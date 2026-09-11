package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import org.junit.jupiter.api.Test;

class PaqueteTest {

  @Test
  void rechazaPesoEnCero() {
    assertThrows(ExcepcionDeDominio.class, () -> new Paquete(0, 30, 25, 4));
  }

  @Test
  void rechazaPesoNegativo() {
    assertThrows(ExcepcionDeDominio.class, () -> new Paquete(-1, 30, 25, 4));
  }

  @Test
  void rechazaLargoEnCero() {
    assertThrows(ExcepcionDeDominio.class, () -> new Paquete(180, 0, 25, 4));
  }

  @Test
  void rechazaAnchoEnCero() {
    assertThrows(ExcepcionDeDominio.class, () -> new Paquete(180, 30, 0, 4));
  }

  @Test
  void rechazaAltoEnCero() {
    assertThrows(ExcepcionDeDominio.class, () -> new Paquete(180, 30, 25, 0));
  }

  /**
   * Sin topes máximos a propósito: los límites son de cada transportadora y todavía no están
   * confirmados. Un paquete grande se cotiza y la transportadora dirá que no; un tope inventado
   * aquí rechazaría la venta antes de preguntar.
   */
  @Test
  void aceptaUnPaqueteGrandeSinTopeInventado() {
    assertDoesNotThrow(() -> new Paquete(50_000, 200, 150, 150));
  }

  @Test
  void conservaLasCuatroMedidas() {
    Paquete paquete = new Paquete(180, 30, 25, 4);

    assertEquals(180, paquete.pesoGramos());
    assertEquals(30, paquete.largoCm());
    assertEquals(25, paquete.anchoCm());
    assertEquals(4, paquete.altoCm());
  }
}
