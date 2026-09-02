package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Sku;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class VarianteTest {

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
                List.of()));
  }

  @Test
  void codigoBarrasVacioSeGuardaComoAusente() {
    Variante variante =
        Variante.crear(
            new Sku("TS-1"), Dinero.deCop(100_000), new BigDecimal("0.19"), 10, "  ", List.of());

    assertTrue(variante.codigoBarras().isEmpty());
  }
}
