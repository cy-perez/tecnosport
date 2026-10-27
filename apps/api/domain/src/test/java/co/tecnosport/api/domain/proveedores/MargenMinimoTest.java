package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import org.junit.jupiter.api.Test;

class MargenMinimoTest {

  /** La fórmula de la regla 18: (precio − costo) / costo ≥ 0,10. El borde exacto entra. */
  @Test
  void elDiezPorCientoExactoAlcanzaYUnPesoMenosNo() {
    assertTrue(MargenMinimo.alcanza(Dinero.deCop(110_000), Dinero.deCop(100_000)));
    assertFalse(MargenMinimo.alcanza(Dinero.deCop(109_999), Dinero.deCop(100_000)));
    assertFalse(
        MargenMinimo.alcanza(Dinero.deCop(100_000), Dinero.deCop(100_000)), "al costo no alcanza");
  }

  @Test
  void exigirLanzaConElCodigoQueElPanelTraduce() {
    assertThrows(
        PrecioSinMargenMinimoException.class,
        () -> MargenMinimo.exigir(Dinero.deCop(105_000), Dinero.deCop(100_000), "el bolso"));
    assertDoesNotThrow(
        () -> MargenMinimo.exigir(Dinero.deCop(110_000), Dinero.deCop(100_000), "el bolso"));
  }
}
