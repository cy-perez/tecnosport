package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CalculadoraDeMargenTest {

  /** Los del negocio desde el 3 de octubre de 2026. */
  private static final TopesDeGanancia TOPES =
      new TopesDeGanancia(Dinero.deCop(20000), Dinero.deCop(30000));

  /** Topes que no tocan nada, para probar solo el factor y el redondeo. */
  private static final TopesDeGanancia SIN_TOPES =
      new TopesDeGanancia(Dinero.deCop(0), Dinero.deCop(10_000_000));

  private static Dinero sugerir(long proveedor, String factor, TopesDeGanancia topes) {
    return CalculadoraDeMargen.sugerir(Dinero.deCop(proveedor), new BigDecimal(factor), topes);
  }

  @Test
  void multiplicaYSubeALaUnidadDeMilSiguiente() {
    // 53.000 × 1,35 = 71.550 → 72.000
    assertEquals(Dinero.deCop(72000), sugerir(53000, "1.35", SIN_TOPES));
    // 45.000 × 1,30 = 58.500 → 59.000: la mitad también sube
    assertEquals(Dinero.deCop(59000), sugerir(45000, "1.30", SIN_TOPES));
  }

  /** Los ejemplos del negocio: 72.500 es 73.000 y 78.900 es 79.000. */
  @Test
  void subeAunqueEsteMuyCercaDeLaUnidadDeMilAnterior() {
    assertEquals(Dinero.deCop(73000), sugerir(72500, "1", SIN_TOPES));
    assertEquals(Dinero.deCop(79000), sugerir(78900, "1", SIN_TOPES));
    // 72.001 no se queda en 72.000: siempre la siguiente, nunca la más cercana
    assertEquals(Dinero.deCop(73000), sugerir(72001, "1", SIN_TOPES));
  }

  @Test
  void unPrecioYaRedondoNoSube() {
    assertEquals(Dinero.deCop(79000), sugerir(79000, "1", SIN_TOPES));
  }

  /** 53.000 × 1,35 = 72.000 gana 19.000: sube hasta ganar 20.000. */
  @Test
  void unaGananciaPorDebajoDelMinimoSubeAlMinimo() {
    assertEquals(Dinero.deCop(73000), sugerir(53000, "1.35", TOPES));
  }

  /** 200.000 × 1,35 = 270.000 gana 70.000: baja hasta ganar 30.000. */
  @Test
  void unaGananciaPorEncimaDelMaximoBajaAlMaximo() {
    assertEquals(Dinero.deCop(230000), sugerir(200000, "1.35", TOPES));
  }

  /** 80.000 × 1,30 = 104.000 gana 24.000: entre los topes, el factor manda. */
  @Test
  void entreLosTopesMandaElFactor() {
    assertEquals(Dinero.deCop(104000), sugerir(80000, "1.30", TOPES));
  }

  /** Los bordes son inclusivos: ganar justo 20.000 o justo 30.000 no se toca. */
  @Test
  void losBordesSonInclusivos() {
    // 80.000 × 1,25 = 100.000 gana 20.000
    assertEquals(Dinero.deCop(100000), sugerir(80000, "1.25", TOPES));
    // 100.000 × 1,30 = 130.000 gana 30.000
    assertEquals(Dinero.deCop(130000), sugerir(100000, "1.30", TOPES));
  }

  /**
   * Con un precio del proveedor que no es redondo, el tope se cumple igual: el redondeo va hacia
   * dentro. 53.050 + 20.000 = 73.050 → 74.000, no 73.000 (que ganaría 19.950).
   */
  @Test
  void conUnPrecioNoRedondoElTopeSeCumple() {
    assertEquals(Dinero.deCop(74000), sugerir(53050, "1.35", TOPES));
    // 200.050 + 30.000 = 230.050 → 230.000, no 231.000 (que ganaría 30.950)
    assertEquals(Dinero.deCop(230000), sugerir(200050, "1.35", TOPES));
  }

  /**
   * 100.500 × 1,30 = 130.650 gana 30.150 y ya se pasa; subirlo a 131.000 ganaría 30.500. Manda el
   * máximo hacia abajo: 130.500 → 130.000. Es el caso en que el máximo no deja ir a la siguiente.
   */
  @Test
  void elMaximoBajaALaUnidadDeMilAnterior() {
    assertEquals(Dinero.deCop(130000), sugerir(100500, "1.30", TOPES));
  }

  /**
   * 76.700 × 1,30 = 99.710 gana 23.010, entre los topes; subirlo a 100.000 gana 23.300, aún entre
   * los topes. Pero 99.900 × 1,30 = 129.870 → 130.000 gana 30.100: lo que se pasa es el redondeo,
   * no el factor, y también ahí manda el máximo.
   */
  @Test
  void siElRedondeoEsLoQueSePasaDelMaximoTambienBaja() {
    assertEquals(Dinero.deCop(100000), sugerir(76700, "1.30", TOPES));
    assertEquals(Dinero.deCop(129000), sugerir(99900, "1.30", TOPES));
  }

  /**
   * 53.020 + 20.000 = 73.020: hacia arriba es 74.000; al más cercano sería 73.000, que gana 19.980
   * y rompe el mínimo. Este caso es el que distingue los dos redondeos.
   */
  @Test
  void elMinimoRedondeaHaciaArribaYNoAlMasCercano() {
    assertEquals(Dinero.deCop(74000), sugerir(53020, "1.35", TOPES));
  }

  /** Mínimo y máximo iguales: subir a la unidad de mil no puede ganar más que el máximo. */
  @Test
  void conTopesIgualesNoSePasaDelMaximo() {
    TopesDeGanancia iguales = new TopesDeGanancia(Dinero.deCop(20000), Dinero.deCop(20000));

    assertEquals(Dinero.deCop(60000), sugerir(40050, "1", iguales));
  }

  @Test
  void unFactorDeUnoConTopesGanaAlMenosElMinimo() {
    assertEquals(Dinero.deCop(53000), sugerir(53000, "1", SIN_TOPES));
    assertEquals(Dinero.deCop(73000), sugerir(53000, "1", TOPES));
  }

  @Test
  void unFactorPorDebajoDeUnoNoEsUnMargen() {
    assertThrows(ExcepcionDeDominio.class, () -> sugerir(53000, "0.9", TOPES));
    assertThrows(
        ExcepcionDeDominio.class, () -> CalculadoraDeMargen.sugerir(null, BigDecimal.ONE, TOPES));
    assertThrows(
        ExcepcionDeDominio.class,
        () -> CalculadoraDeMargen.sugerir(Dinero.deCop(1000), BigDecimal.ONE, null));
  }

  @Test
  void losTopesNoSeCruzan() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new TopesDeGanancia(Dinero.deCop(30000), Dinero.deCop(20000)));
  }
}
