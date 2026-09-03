package co.tecnosport.api.infrastructure.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import org.junit.jupiter.api.Test;

/**
 * No hay un vector de prueba de Wompi fijado aquí a propósito: no hay forma de verificar contra su
 * documentación en este momento con la confianza que exige la regla dura #9. Estas pruebas
 * verifican la forma del algoritmo (determinista, sensible a cada entrada, hex de 64 caracteres de
 * un SHA-256) — la verificación contra Wompi real queda para cuando haya credenciales de sandbox.
 */
class WompiClientTest {

  private static final ReferenciaPago REFERENCIA = new ReferenciaPago("TS-2026-000001-1");
  private static final Dinero MONTO = Dinero.deCop(100_000);

  @Test
  void esDeterministaParaLosMismosDatos() {
    WompiClient cliente = new WompiClient("secreto-de-prueba");

    String primera = cliente.generarFirmaIntegridad(REFERENCIA, MONTO);
    String segunda = cliente.generarFirmaIntegridad(REFERENCIA, MONTO);

    assertEquals(primera, segunda);
  }

  @Test
  void esUnHexadecimalDeSesentaYCuatroCaracteres() {
    WompiClient cliente = new WompiClient("secreto-de-prueba");

    String firma = cliente.generarFirmaIntegridad(REFERENCIA, MONTO);

    assertEquals(64, firma.length());
    assertTrue(firma.matches("^[0-9a-f]{64}$"));
  }

  @Test
  void cambiaSiCambiaElMonto() {
    WompiClient cliente = new WompiClient("secreto-de-prueba");

    String firmaOriginal = cliente.generarFirmaIntegridad(REFERENCIA, MONTO);
    String firmaConOtroMonto = cliente.generarFirmaIntegridad(REFERENCIA, Dinero.deCop(200_000));

    assertNotEquals(firmaOriginal, firmaConOtroMonto);
  }

  @Test
  void cambiaSiCambiaLaReferencia() {
    WompiClient cliente = new WompiClient("secreto-de-prueba");

    String firmaOriginal = cliente.generarFirmaIntegridad(REFERENCIA, MONTO);
    String firmaConOtraReferencia =
        cliente.generarFirmaIntegridad(new ReferenciaPago("TS-2026-000001-2"), MONTO);

    assertNotEquals(firmaOriginal, firmaConOtraReferencia);
  }

  @Test
  void cambiaSiCambiaElSecreto() {
    WompiClient primero = new WompiClient("secreto-uno");
    WompiClient segundo = new WompiClient("secreto-dos");

    assertNotEquals(
        primero.generarFirmaIntegridad(REFERENCIA, MONTO),
        segundo.generarFirmaIntegridad(REFERENCIA, MONTO));
  }

  @Test
  void secretoVacioSeRechazaAlConstruir() {
    assertThrows(IllegalArgumentException.class, () -> new WompiClient("  "));
  }

  @Test
  void secretoNuloSeRechazaAlConstruir() {
    assertThrows(IllegalArgumentException.class, () -> new WompiClient(null));
  }
}
