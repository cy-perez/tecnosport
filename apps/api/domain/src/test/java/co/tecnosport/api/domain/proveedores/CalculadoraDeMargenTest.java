package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CalculadoraDeMargenTest {

  @Test
  void multiplicaYRedondeaALaCentena() {
    // 53.000 × 1,35 = 71.550 → 71.600
    assertEquals(
        Dinero.deCop(71600),
        CalculadoraDeMargen.sugerir(Dinero.deCop(53000), new BigDecimal("1.35")));
    // 45.000 × 1,30 = 58.500, ya en la centena
    assertEquals(
        Dinero.deCop(58500),
        CalculadoraDeMargen.sugerir(Dinero.deCop(45000), new BigDecimal("1.30")));
    // 62.000 × 1,35 = 83.700
    assertEquals(
        Dinero.deCop(83700),
        CalculadoraDeMargen.sugerir(Dinero.deCop(62000), new BigDecimal("1.35")));
  }

  /** HALF_UP en la mitad exacta: 43.000 × 1,35 = 58.050 → 58.100, no 58.000. */
  @Test
  void enLaMitadExactaRedondeaHaciaArriba() {
    assertEquals(
        Dinero.deCop(58100),
        CalculadoraDeMargen.sugerir(Dinero.deCop(43000), new BigDecimal("1.35")));
  }

  @Test
  void unFactorDeUnoDejaElPrecioComoEsta() {
    assertEquals(
        Dinero.deCop(53000), CalculadoraDeMargen.sugerir(Dinero.deCop(53000), BigDecimal.ONE));
  }

  @Test
  void unFactorPorDebajoDeUnoNoEsUnMargen() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> CalculadoraDeMargen.sugerir(Dinero.deCop(53000), new BigDecimal("0.9")));
    assertThrows(ExcepcionDeDominio.class, () -> CalculadoraDeMargen.sugerir(null, BigDecimal.ONE));
  }
}
