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
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VarianteTest {

  private static final Paquete PAQUETE = new Paquete(180, 30, 25, 4);

  /*
   * Aquí había una prueba de que la existencia no podía ser negativa. La invariante no se perdió:
   * se mudó, porque desde adr/0050 una variante no guarda ninguna existencia. Quien se niega a
   * dejar el saldo en negativo es `Inventario.registrarAjuste`, y lo prueba `InventarioTest`.
   */

  @Test
  void rechazaTasaIvaMayorAUno() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            Variante.crear(
                new Sku("TS-1"),
                Dinero.deCop(100_000),
                new BigDecimal("1.5"),
                null,
                PAQUETE,
                List.of()));
  }

  /**
   * Lo contrario de lo que esta prueba afirmaba hasta el 19 de septiembre de 2026: la invariante de
   * adr/0021 —sin paquete no se puede construir— se levantó en adr/0046. Una variante sin medir se
   * vende, pero solo con recogida en el punto, y quien se topa con eso es el armador de bultos.
   */
  @Test
  void aceptaUnaVarianteSinPaquete() {
    Variante variante =
        Variante.crear(
            new Sku("TS-1"), Dinero.deCop(100_000), new BigDecimal("0.19"), null, null, List.of());

    assertTrue(variante.paquete().isEmpty());
  }

  @Test
  void aceptaVarianteValida() {
    assertDoesNotThrow(
        () ->
            Variante.crear(
                new Sku("TS-1"),
                Dinero.deCop(100_000),
                new BigDecimal("0.19"),
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
            null,
            PAQUETE,
            List.of());

    assertEquals(Optional.of(PAQUETE), variante.paquete());
  }

  @Test
  void codigoBarrasVacioSeGuardaComoAusente() {
    Variante variante =
        Variante.crear(
            new Sku("TS-1"),
            Dinero.deCop(100_000),
            new BigDecimal("0.19"),
            "  ",
            PAQUETE,
            List.of());

    assertTrue(variante.codigoBarras().isEmpty());
  }
}
