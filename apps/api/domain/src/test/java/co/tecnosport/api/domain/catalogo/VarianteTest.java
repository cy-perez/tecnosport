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

  // --- Precio de contado y publicado (ADR-0095) ---

  private static Variante deContadoYPublicado(long contado, long publicado) {
    return Variante.crear(
        new Sku("TS-1"),
        Dinero.deCop(contado),
        Dinero.deCop(publicado),
        BigDecimal.ZERO,
        null,
        PAQUETE,
        List.of());
  }

  @Test
  void guardaLosDosPrecios() {
    Variante variante = deContadoYPublicado(230_000, 239_000);

    assertEquals(Dinero.deCop(230_000), variante.precioContado());
    assertEquals(Dinero.deCop(239_000), variante.precio());
  }

  /** Sin descuento por pagar sin pasarela, los dos precios son el mismo, y eso vale. */
  @Test
  void conUnSoloPrecioElContadoEsElPublicado() {
    Variante variante =
        Variante.crear(
            new Sku("TS-1"), Dinero.deCop(100_000), BigDecimal.ZERO, null, null, List.of());

    assertEquals(variante.precio(), variante.precioContado());
  }

  /**
   * El publicado es el máximo que alguien paga: por debajo del contado, el descuento es recargo.
   */
  @Test
  void rechazaUnPublicadoMenorQueElContado() {
    ExcepcionDeDominio error =
        assertThrows(ExcepcionDeDominio.class, () -> deContadoYPublicado(230_000, 229_999));

    assertTrue(error.getMessage().contains("TS-1"));
  }

  @Test
  void rechazaUnContadoEnCero() {
    assertThrows(ExcepcionDeDominio.class, () -> deContadoYPublicado(0, 1_000));
  }

  @Test
  void conPreciosCambiaLosDosYNadaMas() {
    Variante variante = deContadoYPublicado(230_000, 239_000);

    Variante cambiada = variante.conPrecios(Dinero.deCop(100_000), Dinero.deCop(105_000));

    assertEquals(Dinero.deCop(100_000), cambiada.precioContado());
    assertEquals(Dinero.deCop(105_000), cambiada.precio());
    assertEquals(variante.id(), cambiada.id());
    assertEquals(variante.paquete(), cambiada.paquete());
  }

  @Test
  void conPreciosNoAceptaUnPublicadoMenorQueElContado() {
    Variante variante = deContadoYPublicado(230_000, 239_000);

    assertThrows(
        ExcepcionDeDominio.class,
        () -> variante.conPrecios(Dinero.deCop(240_000), Dinero.deCop(239_000)));
  }

  /** Medirla, cambiarle el publicado o un atributo no le borra el precio de contado. */
  @Test
  void lasCopiasConservanElContado() {
    Variante variante = deContadoYPublicado(230_000, 239_000);

    assertEquals(
        Dinero.deCop(230_000), variante.medida(new Paquete(300, 30, 25, 5)).precioContado());
    Variante republicada = variante.conPrecio(Dinero.deCop(240_000));
    assertEquals(Dinero.deCop(230_000), republicada.precioContado());
    assertEquals(Dinero.deCop(240_000), republicada.precio());
    Atributo color = Atributo.crear("Color", TipoAtributo.COLOR, List.of());
    Variante conColor = variante.conAtributo(ValorAtributo.de(color, "Negro"));
    assertEquals(Dinero.deCop(230_000), conColor.precioContado());
    assertEquals(
        Dinero.deCop(230_000), conColor.conValor(ValorAtributo.de(color, "Vino")).precioContado());
  }
}
