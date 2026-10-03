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
  void multiplicaYRedondeaALaCentena() {
    // 53.000 × 1,35 = 71.550 → 71.600
    assertEquals(Dinero.deCop(71600), sugerir(53000, "1.35", SIN_TOPES));
    // 45.000 × 1,30 = 58.500, ya en la centena
    assertEquals(Dinero.deCop(58500), sugerir(45000, "1.30", SIN_TOPES));
  }

  /** HALF_UP en la mitad exacta: 43.000 × 1,35 = 58.050 → 58.100, no 58.000. */
  @Test
  void enLaMitadExactaRedondeaHaciaArriba() {
    assertEquals(Dinero.deCop(58100), sugerir(43000, "1.35", SIN_TOPES));
  }

  /** 53.000 × 1,35 = 71.600 gana 18.600: sube hasta ganar 20.000. */
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
   * dentro. 53.050 + 20.000 = 73.050 → 73.100, no 73.000 (que ganaría 19.950).
   */
  @Test
  void conUnPrecioNoRedondoElTopeSeCumple() {
    assertEquals(Dinero.deCop(73100), sugerir(53050, "1.35", TOPES));
    // 200.050 + 30.000 = 230.050 → 230.000, no 230.100 (que ganaría 30.050)
    assertEquals(Dinero.deCop(230000), sugerir(200050, "1.35", TOPES));
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
