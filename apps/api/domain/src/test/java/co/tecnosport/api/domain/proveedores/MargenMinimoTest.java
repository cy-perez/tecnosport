package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import org.junit.jupiter.api.Test;

/**
 * La regla por línea: tecnología entre 50.000 y 400.000 de ganancia (29 de octubre de 2026),
 * prendas con 20.000 de mínimo. El borde exacto entra; un peso de más o de menos, no.
 */
class MargenMinimoTest {

  private static final MargenMinimo TECNOLOGIA =
      MargenMinimo.deTecnologia(Dinero.deCop(50_000), Dinero.deCop(400_000));
  private static final MargenMinimo PRENDAS = MargenMinimo.dePrendas(Dinero.deCop(20_000));

  /** El equipo de 1.900.000 que se quiere vender a 1.950.000: el 10 % pedía 190.000. */
  @Test
  void enTecnologiaBastanCincuentaMilSeaCualSeaElCosto() {
    Dinero costo = Dinero.deCop(1_900_000);
    assertTrue(TECNOLOGIA.alcanza(Dinero.deCop(1_950_000), costo));
    assertFalse(TECNOLOGIA.alcanza(Dinero.deCop(1_949_999), costo));
    assertTrue(TECNOLOGIA.alcanza(Dinero.deCop(150_000), Dinero.deCop(100_000)));
    assertFalse(TECNOLOGIA.alcanza(Dinero.deCop(149_999), Dinero.deCop(100_000)));
  }

  /** El techo solo lo exige la aprobación: 400.000 entra, un peso más no. */
  @Test
  void enTecnologiaAprobarExigeNoPasarDeLaGananciaMaxima() {
    Dinero costo = Dinero.deCop(5_000_000);
    assertDoesNotThrow(() -> TECNOLOGIA.exigir(Dinero.deCop(5_400_000), costo, "el S25"));
    PrecioSobreGananciaMaximaException excepcion =
        assertThrows(
            PrecioSobreGananciaMaximaException.class,
            () -> TECNOLOGIA.exigir(Dinero.deCop(5_400_001), costo, "el S25"));
    assertTrue(excepcion.getMessage().contains("400000 pesos"), excepcion.getMessage());
    assertTrue(TECNOLOGIA.alcanza(Dinero.deCop(5_400_001), costo), "vender no se corta por esto");
  }

  @Test
  void laMinimaDeTecnologiaNoPasaDeLaMaxima() {
    assertThrows(
        IllegalArgumentException.class,
        () -> MargenMinimo.deTecnologia(Dinero.deCop(500_000), Dinero.deCop(400_000)));
  }

  /** En las prendas el mínimo son los pesos de la sugerencia, y no hay techo al aprobar. */
  @Test
  void enPrendasMandaLaGananciaMinimaEnPesosSinTecho() {
    Dinero costo = Dinero.deCop(500_000);
    assertTrue(PRENDAS.alcanza(Dinero.deCop(520_000), costo));
    assertFalse(PRENDAS.alcanza(Dinero.deCop(519_999), costo));
    assertDoesNotThrow(() -> PRENDAS.exigir(Dinero.deCop(2_000_000), costo, "el bolso"));
  }

  @Test
  void alCostoNoAlcanzaEnNingunaLinea() {
    assertFalse(TECNOLOGIA.alcanza(Dinero.deCop(100_000), Dinero.deCop(100_000)));
    assertFalse(PRENDAS.alcanza(Dinero.deCop(100_000), Dinero.deCop(100_000)));
  }

  /** El mensaje sale como detail: dice el mínimo en pesos. */
  @Test
  void exigirLanzaConLaGananciaMinimaEnPesos() {
    PrecioSinMargenMinimoException tecnologia =
        assertThrows(
            PrecioSinMargenMinimoException.class,
            () -> TECNOLOGIA.exigir(Dinero.deCop(1_940_000), Dinero.deCop(1_900_000), "el A17"));
    assertTrue(tecnologia.getMessage().contains("50000 pesos"), tecnologia.getMessage());
    assertFalse(tecnologia.getMessage().contains("%"), tecnologia.getMessage());

    PrecioSinMargenMinimoException prenda =
        assertThrows(
            PrecioSinMargenMinimoException.class,
            () -> PRENDAS.exigir(Dinero.deCop(110_000), Dinero.deCop(100_000), "el bolso"));
    assertTrue(prenda.getMessage().contains("20000 pesos"), prenda.getMessage());
  }
}
