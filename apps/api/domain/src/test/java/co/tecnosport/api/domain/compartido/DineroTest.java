package co.tecnosport.api.domain.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DineroTest {

  @Test
  void redondeaHaciaArribaEnMediosPesos() {
    Dinero dinero = Dinero.deCop(new BigDecimal("1899.5"));

    assertEquals(new BigDecimal("1900"), dinero.valor());
  }

  @Test
  void rechazaValorNegativo() {
    assertThrows(DineroInvalidoException.class, () -> Dinero.deCop(new BigDecimal("-1")));
  }

  @Test
  void aceptaCero() {
    assertEquals(BigDecimal.ZERO, Dinero.deCop(0L).valor());
  }
}
