package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.tecnosport.api.domain.pedido.MetodoPago;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Los valores salen de la documentación pública de Wompi consultada el 14 de septiembre de 2026
 * (regla dura #9), no de memoria. Si Wompi añade o renombra uno, esta prueba no se entera sola —
 * pero deja escrito contra qué lista se escribió el mapeo.
 */
class MediosDeWompiTest {

  @Test
  void traduceLosMediosQueEsteSitioOfrece() {
    assertEquals(MetodoPago.TARJETA, MediosDeWompi.aMetodoPago("CARD"));
    assertEquals(MetodoPago.NEQUI, MediosDeWompi.aMetodoPago("NEQUI"));
    assertEquals(MetodoPago.PSE, MediosDeWompi.aMetodoPago("PSE"));
    assertEquals(MetodoPago.BANCOLOMBIA, MediosDeWompi.aMetodoPago("BANCOLOMBIA_TRANSFER"));
    assertEquals(MetodoPago.BANCOLOMBIA, MediosDeWompi.aMetodoPago("BANCOLOMBIA_QR"));
  }

  /**
   * Medios que Wompi documenta y este sitio no ofrece. {@code null} es "no sé traducir esto", no
   * "esto no coincide": quien pregunte tiene que distinguir los dos casos antes de afirmar una
   * discrepancia. {@code BANCOLOMBIA_COLLECT} está aquí a propósito — es efectivo en un
   * corresponsal, no el botón de Bancolombia, y parecerse al nombre no basta para darlo por
   * equivalente.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "BANCOLOMBIA_COLLECT",
        "BANCOLOMBIA_BNPL",
        "DAVIPLATA",
        "PCOL",
        "SU_PLUS",
        "UN_MEDIO_QUE_NO_EXISTE",
        ""
      })
  void loQueNoSabeTraducirDevuelveNulo(String medioWompi) {
    assertNull(MediosDeWompi.aMetodoPago(medioWompi));
  }

  @Test
  void unMedioAusenteNoRevienta() {
    assertNull(MediosDeWompi.aMetodoPago(null));
  }
}
