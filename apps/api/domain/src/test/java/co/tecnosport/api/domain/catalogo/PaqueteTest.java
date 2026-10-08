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

  /** Lo que el formulario de la plataforma acepta: kilos enteros, y hacia arriba (adr/0071). */
  @Test
  void unaFraccionDeKiloSubeAlKiloEntero() {
    assertEquals(1000, new Paquete(190, 30, 25, 4).alKiloSiguiente().pesoGramos());
    assertEquals(2000, new Paquete(1600, 30, 25, 4).alKiloSiguiente().pesoGramos());
    assertEquals(2000, new Paquete(1001, 30, 25, 4).alKiloSiguiente().pesoGramos());
  }

  @Test
  void unKiloExactoNoSube() {
    assertEquals(1000, new Paquete(1000, 30, 25, 4).alKiloSiguiente().pesoGramos());
    assertEquals(3000, new Paquete(3000, 30, 25, 4).alKiloSiguiente().pesoGramos());
  }

  @Test
  void redondearElPesoNoTocaLasMedidas() {
    Paquete redondeado = new Paquete(190, 30, 25, 4).alKiloSiguiente();

    assertEquals(30, redondeado.largoCm());
    assertEquals(25, redondeado.anchoCm());
    assertEquals(4, redondeado.altoCm());
  }

  /** Cerca del tope de un int, sumar antes de dividir daría la vuelta a un peso negativo. */
  @Test
  void redondearCercaDelTopeNoDesborda() {
    Paquete enorme = new Paquete(Integer.MAX_VALUE, 40, 30, 10).alKiloSiguiente();

    assertEquals(2_147_483_000, enorme.pesoGramos());
  }
}
