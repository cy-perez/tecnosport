package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.ArmadorDeSets.SetArmado;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * El acompañante y el combo que el anuncio escribe y el modelo no devolvió, con los anuncios de La
 * Riverah del 9 de octubre de 2026.
 */
class AcompananteEscritoTest {

  private static final String OVERSIZE =
      """
      Camisetas Oversize para  Caballero
            👕👕👕
      ⭕ Cuello redondo
      ⭕ *PREMIUN*
      ⭕ Talla S M L XL (Horma real, amplia)\s

        Camiseta 🎽55.000~~
      Promo 4x200.000
      Nota: Las gorras tiene un valor de 35mil-

      *LÍNEA EXCLUSIVA PARA PEDIDO*
      📦*+57 301 9854097*
      """;

  private static final String POLO =
      """
      CAMISETA POLO 1.1 👦👦
      ⭕ tipo polo
      ⭕Calidad 1.1
      ⭕M L Xl 2XL

        🤑  $55.000🥳
      Promo 4x200.000

      Gorra 🧢 $35.000
      Polo+ gorra $ 85.000
      """;

  private static final String SEDA_FRIA =
      """
      ~Camiseta 🤑$35.000🥳
      Promo 10x300.000

      🧢gorra $35.000
      Dúo 🧢👕 camiseta/gorra $65.000
      """;

  private static final String TALLA_PLUS =
      """
       Camiseta 🤑$65.000🥳
      Gorra $35.000
      Dúo $95.000

      *LÍNEA EXCLUSIVA PARA PEDIDO*
      📦*+57 301 9854097*
      """;

  private static final String FALDA =
      """
      ✅Falda de dama
      ✅Talla  S M L

      Falda 🤑$59.900
      Básica 48.000
      """;

  private static PrecioAdicional gorra(long precio) {
    return new PrecioAdicional(
        "Gorra", Dinero.deCop(precio), ClaseDePrecioAdicional.ACOMPANANTE, PrendaAcompanante.GORRA);
  }

  private static PrecioAdicional combo(long precio, PrendaAcompanante prenda) {
    return new PrecioAdicional("Combo", Dinero.deCop(precio), ClaseDePrecioAdicional.COMBO, prenda);
  }

  @Test
  void laNotaDeLasGorrasEsUnAcompananteDe35Mil() {
    assertEquals(List.of(gorra(35000)), AcompananteEscrito.en(OVERSIZE));
  }

  @Test
  void laGorraYElComboSeLeenCadaUnoEnSuLinea() {
    assertEquals(
        List.of(gorra(35000), combo(85000, PrendaAcompanante.GORRA)), AcompananteEscrito.en(POLO));
    assertEquals(
        List.of(gorra(35000), combo(65000, PrendaAcompanante.GORRA)),
        AcompananteEscrito.en(SEDA_FRIA));
  }

  /** «Dúo $95.000» no dice la prenda: es la del único acompañante del texto, la gorra. */
  @Test
  void elDuoSinPrendaEsDeLaGorraDelTexto() {
    assertEquals(
        List.of(gorra(35000), combo(95000, PrendaAcompanante.GORRA)),
        AcompananteEscrito.en(TALLA_PLUS));
  }

  /** La línea del pedido lleva un «+» y cifras: no es un combo de nueve millones. */
  @Test
  void laLineaDelTelefonoNoEsUnCombo() {
    assertEquals(List.of(), AcompananteEscrito.en("Dúo para pedidos\n📦*+57 301 9854097*"));
  }

  @Test
  void laBasicaConSuPrecioEsLaBlusa() {
    assertEquals(
        List.of(
            new PrecioAdicional(
                "Básica",
                Dinero.deCop(48000),
                ClaseDePrecioAdicional.ACOMPANANTE,
                PrendaAcompanante.BLUSA)),
        AcompananteEscrito.en(FALDA));
  }

  @Test
  void laBasicaQueEsLaPrendaNoTraePrecioEnSuLineaYNoEsAcompanante() {
    assertTrue(
        AcompananteEscrito.en(
                "BÁSICA RAYA  AMERICANAS* 🥰💙🥰💙\n❤️TELA  Licrada\n🤑 *$49.900* 🥳🥳")
            .isEmpty());
  }

  @Test
  void elSetDeFaldaYBasicaEsUnComboDeLaBlusa() {
    assertEquals(
        List.of(combo(88000, PrendaAcompanante.BLUSA)),
        AcompananteEscrito.en("Falda 🤑$49.900\nSet, falda + básica $ 88.000"));
  }

  @Test
  void sinTextoNoHayNada() {
    assertTrue(AcompananteEscrito.en(null).isEmpty());
  }

  private static ProductoExtraido delModelo(
      String titulo, TipoProductoProveedor tipo, long precio, List<PrecioAdicional> adicionales) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.ROPA,
        tipo,
        Dinero.deCop(precio),
        Tallas.lista(List.of("S", "M", "L", "XL")),
        null,
        List.of(),
        null,
        "Una prenda.",
        "A garment for men",
        false,
        new BigDecimal("0.9"),
        null,
        null,
        null,
        adicionales,
        List.of(),
        null);
  }

  @Test
  void laOversizeSinLaGorraDelModeloSaleComoSetDe90Mil() {
    // Lo que devolvió el modelo el 12 de octubre de 2026: la camiseta y la promoción, sin la gorra.
    ProductoExtraido contrastado =
        delModelo(
                "Camiseta oversize para caballero",
                TipoProductoProveedor.CAMISETA,
                55000,
                List.of(
                    new PrecioAdicional(
                        "Promo 4 unidades",
                        Dinero.deCop(200000),
                        ClaseDePrecioAdicional.PROMOCION,
                        null)))
            .contrastadoCon(OVERSIZE);
    SetArmado set = ArmadorDeSets.armar(contrastado, contrastado.precioProveedor()).orElseThrow();

    assertEquals(Dinero.deCop(90000), set.costo());
    assertEquals(OrigenDelCostoDeSet.SUMA_DE_PRENDAS, set.armado().origenDelCosto());
    assertEquals(PrendaAcompanante.GORRA, set.armado().acompanante());
    assertEquals("Set camiseta oversize y gorra", set.producto().titulo());
    assertTrue(set.preciosEscritosEn(OVERSIZE), "los dos precios están escritos en el anuncio");
  }

  /**
   * Sin el combo leído, el polo sin nada del modelo saldría a 90.000, la suma; el anuncio dice
   * «Polo+ gorra $ 85.000».
   */
  @Test
  void elPoloSinNadaDelModeloSaleAlPrecioDelComboYNoALaSuma() {
    ProductoExtraido contrastado =
        delModelo("Polo importado", TipoProductoProveedor.POLO, 55000, List.of())
            .contrastadoCon(POLO);
    SetArmado set = ArmadorDeSets.armar(contrastado, contrastado.precioProveedor()).orElseThrow();

    assertEquals(Dinero.deCop(85000), set.costo());
    assertEquals(OrigenDelCostoDeSet.ANUNCIADO, set.armado().origenDelCosto());
  }

  @Test
  void elAcompananteQueElModeloSiDevolvioNoSeRepite() {
    ProductoExtraido contrastado =
        delModelo(
                "Falda de dama",
                TipoProductoProveedor.FALDA,
                59900,
                List.of(
                    new PrecioAdicional(
                        "Básica",
                        Dinero.deCop(48000),
                        ClaseDePrecioAdicional.ACOMPANANTE,
                        PrendaAcompanante.BLUSA)))
            .contrastadoCon(FALDA);

    assertEquals(1, contrastado.preciosAdicionales().size());
  }
}
