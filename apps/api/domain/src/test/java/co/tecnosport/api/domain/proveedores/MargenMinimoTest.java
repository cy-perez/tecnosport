package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** La regla por línea del 27 de octubre de 2026. El borde exacto entra; un peso menos, no. */
class MargenMinimoTest {

  private static final MargenMinimo TECNOLOGIA = MargenMinimo.deTecnologia(Dinero.deCop(400_000));
  private static final MargenMinimo PRENDAS = MargenMinimo.dePrendas(Dinero.deCop(20_000));

  /** Por debajo del tope manda el 10 % de la regla 18. */
  @Test
  void enTecnologiaMandaElDiezPorCientoMientrasNoPaseDelTope() {
    Dinero costo = Dinero.deCop(1_000_000);
    assertTrue(TECNOLOGIA.alcanza(Dinero.deCop(1_100_000), costo));
    assertFalse(TECNOLOGIA.alcanza(Dinero.deCop(1_099_999), costo));
  }

  /** Cinco millones pedirían 500.000 con el 10 %: el tope lo baja a 400.000. */
  @Test
  void enTecnologiaElTopeDeGananciaLimitaElMinimo() {
    Dinero costo = Dinero.deCop(5_000_000);
    assertTrue(TECNOLOGIA.alcanza(Dinero.deCop(5_400_000), costo));
    assertFalse(TECNOLOGIA.alcanza(Dinero.deCop(5_399_999), costo));
  }

  /**
   * En una prenda cara el mínimo son los pesos de la sugerencia, no el 10 % (que pediría 50.000).
   */
  @Test
  void enPrendasMandaLaGananciaMinimaEnPesos() {
    Dinero costo = Dinero.deCop(500_000);
    assertTrue(PRENDAS.alcanza(Dinero.deCop(520_000), costo));
    assertFalse(PRENDAS.alcanza(Dinero.deCop(519_999), costo));
  }

  /** Y en una barata también: 70.000 le gana un 40 %, pero lo que cuenta son los 20.000. */
  @Test
  void enPrendasBaratasBastaLaGananciaMinimaAunqueSeaMasDelDiezPorCiento() {
    Dinero costo = Dinero.deCop(50_000);
    assertTrue(PRENDAS.alcanza(Dinero.deCop(70_000), costo));
    assertFalse(PRENDAS.alcanza(Dinero.deCop(69_999), costo));
  }

  @Test
  void alCostoNoAlcanzaEnNingunaLinea() {
    assertFalse(TECNOLOGIA.alcanza(Dinero.deCop(100_000), Dinero.deCop(100_000)));
    assertFalse(PRENDAS.alcanza(Dinero.deCop(100_000), Dinero.deCop(100_000)));
  }

  /** El mensaje sale como detail: dice el mínimo en pesos, redondeado hacia arriba. */
  @Test
  void exigirLanzaConLaGananciaMinimaEnPesos() {
    PrecioSinMargenMinimoException tecnologia =
        assertThrows(
            PrecioSinMargenMinimoException.class,
            () -> TECNOLOGIA.exigir(Dinero.deCop(1_100_000), Dinero.deCop(1_000_005), "el A17"));
    assertTrue(tecnologia.getMessage().contains("100001 pesos"), tecnologia.getMessage());
    assertFalse(tecnologia.getMessage().contains("%"), tecnologia.getMessage());

    PrecioSinMargenMinimoException prenda =
        assertThrows(
            PrecioSinMargenMinimoException.class,
            () -> PRENDAS.exigir(Dinero.deCop(110_000), Dinero.deCop(100_000), "el bolso"));
    assertTrue(prenda.getMessage().contains("20000 pesos"), prenda.getMessage());

    assertDoesNotThrow(
        () -> PRENDAS.exigir(Dinero.deCop(120_000), Dinero.deCop(100_000), "el bolso"));
  }

  @Test
  void laGananciaMinimaNoSeRedondea() {
    assertEquals(
        0,
        TECNOLOGIA.gananciaMinima(Dinero.deCop(1_000_005)).compareTo(new BigDecimal("100000.5")));
  }
}
