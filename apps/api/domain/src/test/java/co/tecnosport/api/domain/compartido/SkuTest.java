package co.tecnosport.api.domain.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SkuTest {

  @Test
  void normalizaAMayusculasYRecortaEspacios() {
    Sku sku = new Sku("  ts-cam-az-m  ");

    assertEquals("TS-CAM-AZ-M", sku.valor());
  }

  @Test
  void rechazaVacio() {
    assertThrows(SkuInvalidoException.class, () -> new Sku("   "));
  }

  @Test
  void rechazaNulo() {
    assertThrows(SkuInvalidoException.class, () -> new Sku(null));
  }
}
