package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * La fórmula de la decisión 1 del ADR-0095, con las tarifas de Wompi leídas el 26 de octubre de
 * 2026: 2,65 % + $700 + IVA del 19 %, o sea {@code (contado + 833) / 0,968465}.
 */
class ComisionDePasarelaTest {

  private static final ComisionDePasarela WOMPI =
      new ComisionDePasarela(new BigDecimal("0.0265"), Dinero.deCop(700), new BigDecimal("0.19"));

  /** Los cuatro ejemplos del ADR, decisión 6. */
  @ParameterizedTest
  @CsvSource({
    // (230.000 + 833) / 0,968465 = 238.349,7
    "230000, 239000",
    // (1.000.000 + 833) / 0,968465 = 1.033.422,0
    "1000000, 1034000",
    // (100.000 + 833) / 0,968465 = 104.116,2
    "100000, 105000",
    // (50.000 + 833) / 0,968465 = 52.488,2, por debajo de 100.000: a la centena
    "50000, 52500",
    // (8.000 + 833) / 0,968465 = 9.120,6: el caso que motivó la centena
    "8000, 9200",
    // (30.000 + 833) / 0,968465 = 31.836,8
    "30000, 31900"
  })
  void publicaLosEjemplosDelAdr(long contado, long publicado) {
    assertEquals(Dinero.deCop(publicado), WOMPI.publicadoDesde(Dinero.deCop(contado)));
  }

  /**
   * Lo que el ADR promete: pagando por Wompi el publicado, al negocio le queda por lo menos el
   * contado. Si el redondeo bajara en vez de subir, esto fallaría.
   */
  @ParameterizedTest
  @CsvSource({"230000", "1000000", "100000", "50000", "1", "89900", "4999999"})
  void loQueQuedaDespuesDeLaComisionCubreElContado(long contado) {
    BigDecimal publicado = WOMPI.publicadoDesde(Dinero.deCop(contado)).valor();
    BigDecimal comision =
        publicado
            .multiply(new BigDecimal("0.0265"))
            .add(BigDecimal.valueOf(700))
            .multiply(new BigDecimal("1.19"));

    assertTrue(
        publicado.subtract(comision).compareTo(BigDecimal.valueOf(contado)) >= 0,
        "con " + contado + " de contado, " + publicado + " publicado no cubre la comisión");
  }

  @Test
  void porDebajoDeCienMilRedondeaHaciaArribaALaCentena() {
    // (100 + 833) / 0,968465 = 963,4 → 1.000
    assertEquals(Dinero.deCop(1_000), WOMPI.publicadoDesde(Dinero.deCop(100)));
    // (1.000 + 833) / 0,968465 = 1.892,7 → 1.900, no 2.000
    assertEquals(Dinero.deCop(1_900), WOMPI.publicadoDesde(Dinero.deCop(1_000)));
  }

  /** El umbral mira el resultado exacto: justo por debajo va a la centena, desde él a miles. */
  @Test
  void elUmbralDeLosMilesEsElResultadoExacto() {
    ComisionDePasarela sinComision =
        new ComisionDePasarela(BigDecimal.ZERO, Dinero.deCop(0), BigDecimal.ZERO);

    assertEquals(Dinero.deCop(99_900), sinComision.publicadoDesde(Dinero.deCop(99_801)));
    assertEquals(Dinero.deCop(100_000), sinComision.publicadoDesde(Dinero.deCop(99_901)));
    assertEquals(Dinero.deCop(100_000), sinComision.publicadoDesde(Dinero.deCop(100_000)));
    assertEquals(Dinero.deCop(101_000), sinComision.publicadoDesde(Dinero.deCop(100_001)));
  }

  /** Un resultado que ya es múltiplo de mil no sube al siguiente. */
  @Test
  void unResultadoExactoEnMilesSeQuedaIgual() {
    ComisionDePasarela sinComision =
        new ComisionDePasarela(BigDecimal.ZERO, Dinero.deCop(0), BigDecimal.ZERO);

    assertEquals(Dinero.deCop(230_000), sinComision.publicadoDesde(Dinero.deCop(230_000)));
    assertEquals(Dinero.deCop(231_000), sinComision.publicadoDesde(Dinero.deCop(230_001)));
  }

  /** El IVA va sobre la comisión, no sobre la venta: sin IVA el publicado es menor. */
  @Test
  void elIvaMultiplicaLasDosPartesDeLaComision() {
    ComisionDePasarela sinIva =
        new ComisionDePasarela(new BigDecimal("0.0265"), Dinero.deCop(700), BigDecimal.ZERO);

    // (1.000.000 + 700) / 0,9735 = 1.027.940,4 → 1.028.000
    assertEquals(Dinero.deCop(1_028_000), sinIva.publicadoDesde(Dinero.deCop(1_000_000)));
  }

  @Test
  void rechazaUnaComisionVariableNegativa() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new ComisionDePasarela(
                new BigDecimal("-0.01"), Dinero.deCop(700), new BigDecimal("0.19")));
  }

  /** 2,65 escrito como si fuera el porcentaje y no la fracción: no arranca. */
  @Test
  void rechazaUnaComisionVariableDeUnoOMas() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new ComisionDePasarela(BigDecimal.ONE, Dinero.deCop(700), new BigDecimal("0.19")));
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new ComisionDePasarela(
                new BigDecimal("2.65"), Dinero.deCop(700), new BigDecimal("0.19")));
  }

  @Test
  void rechazaUnIvaNegativo() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new ComisionDePasarela(
                new BigDecimal("0.0265"), Dinero.deCop(700), new BigDecimal("-0.19")));
  }

  /** 0,9 × 1,2 = 1,08: la comisión se llevaría más que el precio y la fórmula daría negativo. */
  @Test
  void rechazaUnaComisionQueConSuIvaSeLlevaElPrecioEntero() {
    ExcepcionDeDominio error =
        assertThrows(
            ExcepcionDeDominio.class,
            () ->
                new ComisionDePasarela(
                    new BigDecimal("0.9"), Dinero.deCop(700), new BigDecimal("0.2")));

    assertTrue(error.getMessage().contains("0.9"));
  }

  @Test
  void rechazaTarifasNulas() {
    assertThrows(
        NullPointerException.class,
        () -> new ComisionDePasarela(null, Dinero.deCop(700), new BigDecimal("0.19")));
    assertThrows(
        NullPointerException.class,
        () -> new ComisionDePasarela(new BigDecimal("0.0265"), null, new BigDecimal("0.19")));
    assertThrows(
        NullPointerException.class,
        () -> new ComisionDePasarela(new BigDecimal("0.0265"), Dinero.deCop(700), null));
  }
}
