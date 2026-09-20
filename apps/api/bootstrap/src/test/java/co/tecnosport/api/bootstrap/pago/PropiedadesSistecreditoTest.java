package co.tecnosport.api.bootstrap.pago;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Lo que esta clase protege no es la comodidad de configurar: es que un despliegue con la
 * configuración a medias se caiga al arrancar en vez de al primer comprador ({@code adr/0048}).
 */
class PropiedadesSistecreditoTest {

  @Test
  void apagadoNoExigeNadaPorqueNoVaALlamarANadie() {
    assertDoesNotThrow(() -> propiedades(false, null, "Production", false, "Approved"));
    assertNull(propiedades(false, null, "Production", false, "Approved").montoMinimoComoDinero());
  }

  /**
   * El dato que falta y que bloquea. Sin el mínimo del crédito, el checkout ofrecería un método que
   * la pasarela rechaza con su 802 y el comprador vería un error que no explica nada.
   */
  @Test
  void habilitadoSinMontoMinimoNoArranca() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> propiedades(true, null, "Production", false, "Approved"));
    assertTrue(error.getMessage().contains("monto-minimo"));
  }

  @Test
  void habilitadoSinCredencialesNoArranca() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () ->
                new PropiedadesSistecredito(
                    true,
                    "https://api.credinet.co/pay",
                    "",
                    "store",
                    "vendor",
                    "Production",
                    2,
                    10,
                    10,
                    700,
                    false,
                    "Approved",
                    new BigDecimal("30000")));
    assertTrue(error.getMessage().contains("llave-suscripcion"));
  }

  /** {@code SCOrigen} lo compara la pasarela tal cual: "PRODUCTION" o "prod" no son el valor. */
  @Test
  void unAmbienteQueNoEsStagingNiProductionNoArranca() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> propiedades(true, new BigDecimal("30000"), "produccion", false, "Approved"));
    assertTrue(error.getMessage().contains("SCOrigen"));
  }

  /**
   * Un sandbox encendido sin un estado que la pasarela sepa simular es una simulación de nada: la
   * transacción se crearía igual y nadie se enteraría de que el freno no estaba puesto.
   */
  @Test
  void elModoSandboxExigeUnEstadoQueLaPasarelaSepaSimular() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> propiedades(false, null, "Production", true, "Aprobado"));
    assertTrue(error.getMessage().contains("sandbox-estado"));
  }

  @Test
  void conTodoEnSuSitioElMontoMinimoSaleComoDinero() {
    PropiedadesSistecredito propiedades =
        propiedades(true, new BigDecimal("30000"), "Production", false, "Approved");

    assertEquals(Dinero.deCop(30_000), propiedades.montoMinimoComoDinero());
  }

  private PropiedadesSistecredito propiedades(
      boolean habilitado,
      BigDecimal montoMinimo,
      String ambiente,
      boolean sandboxActivo,
      String sandboxEstado) {
    return new PropiedadesSistecredito(
        habilitado,
        "https://api.credinet.co/pay",
        "llave",
        "store",
        "vendor",
        ambiente,
        2,
        10,
        10,
        700,
        sandboxActivo,
        sandboxEstado,
        montoMinimo);
  }
}
