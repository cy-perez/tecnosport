package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Sku;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class VarianteTest {

  private static final Paquete PAQUETE = new Paquete(180, 30, 25, 4);

  @Test
  void rechazaExistenciaNegativa() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            Variante.crear(
                new Sku("TS-1"),
                Dinero.deCop(100_000),
                new BigDecimal("0.19"),
                -1,
                null,
                PAQUETE,
                List.of()));
  }

  @Test
  void rechazaTasaIvaMayorAUno() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            Variante.crear(
                new Sku("TS-1"),
                Dinero.deCop(100_000),
                new BigDecimal("1.5"),
                10,
                null,
                PAQUETE,
                List.of()));
  }

  /**
   * La invariante de adr/0021: sin peso ni dimensiones no hay cotización de envío, así que una
   * variante sin paquete no se puede construir — igual que una sin SKU.
   */
  @Test
  void rechazaVarianteSinPaquete() {
    assertThrows(
        NullPointerException.class,
        () ->
            Variante.crear(
                new Sku("TS-1"),
                Dinero.deCop(100_000),
                new BigDecimal("0.19"),
                10,
                null,
                null,
                List.of()));
  }

  @Test
  void aceptaVarianteValida() {
    assertDoesNotThrow(
        () ->
            Variante.crear(
                new Sku("TS-1"),
                Dinero.deCop(100_000),
                new BigDecimal("0.19"),
                10,
                "7701234567890",
                PAQUETE,
                List.of()));
  }

  @Test
  void conservaElPaquete() {
    Variante variante =
        Variante.crear(
            new Sku("TS-1"),
            Dinero.deCop(100_000),
            new BigDecimal("0.19"),
            10,
            null,
            PAQUETE,
            List.of());

    assertEquals(PAQUETE, variante.paquete());
  }

  @Test
  void codigoBarrasVacioSeGuardaComoAusente() {
    Variante variante =
        Variante.crear(
            new Sku("TS-1"),
            Dinero.deCop(100_000),
            new BigDecimal("0.19"),
            10,
            "  ",
            PAQUETE,
            List.of());

    assertTrue(variante.codigoBarras().isEmpty());
  }
}
