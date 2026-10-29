package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.ArmadorDeSets.SetArmado;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Los sets de La Riverah (decidido por el negocio el 12 de octubre de 2026), con los anuncios del
 * chat del 9 de octubre: la falda con su blusa es un conjunto, la camiseta o el polo con su gorra
 * es un set, y el costo es el del set si el proveedor lo anuncia o la suma de las prendas si no.
 */
class SetsDeProveedorTest {

  private static final TextosDelSet TEXTOS =
      new TextosDelSet("Falda con la blusa que aparece en las fotos.", "Skirt and blouse set");

  private static ProductoExtraido extraido(
      String titulo,
      TipoProductoProveedor tipo,
      long precio,
      List<PrecioAdicional> adicionales,
      TextosDelSet textos) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.ROPA,
        tipo,
        Dinero.deCop(precio),
        Tallas.lista(List.of("S", "M", "L")),
        null,
        List.of(),
        null,
        "La prenda sola.",
        "The garment",
        false,
        new BigDecimal("0.9"),
        null,
        null,
        null,
        adicionales,
        List.of(),
        textos);
  }

  private static PrecioAdicional acompanante(String concepto, long precio, PrendaAcompanante p) {
    return new PrecioAdicional(
        concepto, Dinero.deCop(precio), ClaseDePrecioAdicional.ACOMPANANTE, p);
  }

  private static PrecioAdicional combo(String concepto, long precio, PrendaAcompanante p) {
    return new PrecioAdicional(concepto, Dinero.deCop(precio), ClaseDePrecioAdicional.COMBO, p);
  }

  private static PrecioAdicional promo(long precio) {
    return new PrecioAdicional(
        "Promo 4 unidades", Dinero.deCop(precio), ClaseDePrecioAdicional.PROMOCION, null);
  }

  private static SetArmado armar(ProductoExtraido producto) {
    return ArmadorDeSets.armar(producto, producto.precioProveedor()).orElseThrow();
  }

  // --- Las faldas ---

  @Test
  void faldaConSetAnunciadoEsUnConjuntoAlPrecioDelSet() {
    // «Falda 🤑$49.900 / Set, falda + básica $ 88.000»
    SetArmado set =
        armar(
            extraido(
                "Falda de dama",
                TipoProductoProveedor.FALDA,
                49900,
                List.of(combo("Set falda + básica", 88000, PrendaAcompanante.BLUSA)),
                TEXTOS));

    assertEquals(Dinero.deCop(88000), set.costo());
    assertEquals(Dinero.deCop(88000), set.producto().precioProveedor());
    assertEquals(OrigenDelCostoDeSet.ANUNCIADO, set.armado().origenDelCosto());
    assertEquals(PrendaAcompanante.BLUSA, set.armado().acompanante());
    assertEquals(TipoProductoProveedor.CONJUNTO_FALDA, set.producto().tipo());
    assertEquals("Set falda y blusa", set.producto().titulo());
    assertEquals("Falda con la blusa que aparece en las fotos.", set.producto().descripcion());
    assertEquals("Skirt and blouse set", set.producto().altEn());
    assertEquals(0, set.armado().gramosAdicionales(), "la blusa no suma: Conjuntos pesa 700 g");
  }

  @Test
  void faldaSinPrecioDeSetSumaLaFaldaYLaBlusa() {
    // «Falda 🤑$59.900 / Básica 48.000»
    SetArmado set =
        armar(
            extraido(
                "Falda de dama",
                TipoProductoProveedor.FALDA,
                59900,
                List.of(acompanante("Básica", 48000, PrendaAcompanante.BLUSA)),
                TEXTOS));

    assertEquals(Dinero.deCop(107900), set.costo());
    assertEquals(OrigenDelCostoDeSet.SUMA_DE_PRENDAS, set.armado().origenDelCosto());
    assertEquals(TipoProductoProveedor.CONJUNTO_FALDA, set.producto().tipo());
  }

  @Test
  void faldaShortConSetEsUnConjuntoQueLoDiceEnElTituloEnElOrdenDelProveedor() {
    // «Falda de dama ( tiene short ) … Falda 🤑$59.900 / Set básica + falda $99.000»
    SetArmado set =
        armar(
            extraido(
                "Falda short de dama",
                TipoProductoProveedor.FALDA_SHORT,
                59900,
                List.of(combo("Set básica + falda", 99000, PrendaAcompanante.BLUSA)),
                TEXTOS));

    assertEquals(Dinero.deCop(99000), set.costo());
    assertEquals(TipoProductoProveedor.CONJUNTO_FALDA, set.producto().tipo());
    assertEquals("Set blusa y falda short", set.producto().titulo());
  }

  // --- Las camisetas y los polos ---

  @Test
  void camisetaOversizeConLaNotaDeLaGorraSumaLasDos() {
    // «Camiseta 🎽55.000~~ / Promo 4x200.000 / Nota: Las gorras tiene un valor de 35mil-»
    SetArmado set =
        armar(
            extraido(
                "Camiseta oversize para caballero",
                TipoProductoProveedor.CAMISETA,
                55000,
                List.of(promo(200000), acompanante("Gorras", 35000, PrendaAcompanante.GORRA)),
                null));

    assertEquals(Dinero.deCop(90000), set.costo(), "la promoción no entra en la suma");
    assertEquals(OrigenDelCostoDeSet.SUMA_DE_PRENDAS, set.armado().origenDelCosto());
    assertEquals(TipoProductoProveedor.CAMISETA, set.producto().tipo(), "sigue en Camisetas");
    assertEquals("Set camiseta oversize y gorra", set.producto().titulo());
    assertEquals(100, set.armado().gramosAdicionales(), "la gorra pesa 100 g");
    assertEquals(
        "La prenda sola. Se vende en set con la gorra que aparece en las fotos.",
        set.producto().descripcion(),
        "sin textos del set, la de la prenda dice con qué se vende");
    assertTrue(set.conDescripcionDelSet());
  }

  @Test
  void poloConGorraAlPrecioDelCombo() {
    // «$55.000 / Promo 4x200.000 / Gorra 🧢 $35.000 / Polo+ gorra $ 85.000»
    SetArmado set =
        armar(
            extraido(
                "Polo importado",
                TipoProductoProveedor.POLO,
                55000,
                List.of(
                    promo(200000),
                    acompanante("Gorra", 35000, PrendaAcompanante.GORRA),
                    combo("Polo + gorra", 85000, PrendaAcompanante.GORRA)),
                null));

    assertEquals(Dinero.deCop(85000), set.costo());
    assertEquals(OrigenDelCostoDeSet.ANUNCIADO, set.armado().origenDelCosto());
    assertEquals(TipoProductoProveedor.POLO, set.producto().tipo());
    assertEquals("Set polo importado y gorra", set.producto().titulo());
  }

  @Test
  void camisetaTallaPlusAlPrecioDelDuo() {
    // «Camiseta 🤑$65.000🥳 / Gorra $35.000 / Dúo $95.000»
    SetArmado set =
        armar(
            extraido(
                "Camiseta talla plus para caballero",
                TipoProductoProveedor.CAMISETA,
                65000,
                List.of(
                    acompanante("Gorra", 35000, PrendaAcompanante.GORRA),
                    combo("Dúo", 95000, PrendaAcompanante.GORRA)),
                null));

    assertEquals(Dinero.deCop(95000), set.costo());
    assertEquals("Dúo camiseta talla plus y gorra", set.producto().titulo());
  }

  @Test
  void camisetaSedaFriaAlPrecioDelDuoAunqueNoHayaPrecioDeLaPrenda() {
    // «~Camiseta 🤑$35.000🥳 / Promo 10x300.000 / 🧢gorra $35.000 / Dúo 🧢👕 camiseta/gorra
    // $65.000»
    ProductoExtraido camiseta =
        extraido(
            "Camiseta seda fría para caballero",
            TipoProductoProveedor.CAMISETA,
            35000,
            List.of(
                promo(300000),
                acompanante("Gorra", 35000, PrendaAcompanante.GORRA),
                combo("Dúo camiseta/gorra", 65000, PrendaAcompanante.GORRA)),
            null);

    SetArmado set = ArmadorDeSets.armar(camiseta, null).orElseThrow();

    assertEquals(Dinero.deCop(65000), set.costo());
    assertTrue(
        set.producto().preciosAdicionales().stream()
            .noneMatch(p -> p.clase() == ClaseDePrecioAdicional.PRENDA_SOLA),
        "sin precio de la prenda no hay prenda sola que mostrar");
  }

  @Test
  void elSetConservaElPrecioDeLaPrendaSolaParaQuienRevisa() {
    SetArmado set =
        armar(
            extraido(
                "Falda de dama",
                TipoProductoProveedor.FALDA,
                49900,
                List.of(combo("Set falda + básica", 88000, PrendaAcompanante.BLUSA)),
                TEXTOS));

    PrecioAdicional sola = set.producto().preciosAdicionales().getFirst();
    assertEquals(ClaseDePrecioAdicional.PRENDA_SOLA, sola.clase());
    assertEquals(Dinero.deCop(49900), sola.precio());
    assertEquals("Falda de dama", sola.concepto());
    assertEquals(2, set.producto().preciosAdicionales().size());
  }

  // --- Lo que no arma set ---

  @Test
  void unaPromocionSolaNoArmaNada() {
    // «Promo 4x200.000» en el jean, sin acompañante: el borrador es el jean.
    assertTrue(
        ArmadorDeSets.armar(
                extraido(
                    "Jean", TipoProductoProveedor.PANTALON, 55000, List.of(promo(200000)), null),
                Dinero.deCop(55000))
            .isEmpty());
  }

  @Test
  void unAcompananteQueNoEsBlusaNiGorraNoArmaNada() {
    assertTrue(
        ArmadorDeSets.armar(
                extraido(
                    "Camiseta",
                    TipoProductoProveedor.CAMISETA,
                    55000,
                    List.of(acompanante("Correa", 20000, PrendaAcompanante.OTRA)),
                    null),
                Dinero.deCop(55000))
            .isEmpty());
  }

  @Test
  void unaBlusaConUnaPrendaDeArribaNoEsUnConjunto() {
    assertTrue(
        ArmadorDeSets.armar(
                extraido(
                    "Chaqueta",
                    TipoProductoProveedor.CHAQUETA,
                    90000,
                    List.of(acompanante("Básica", 48000, PrendaAcompanante.BLUSA)),
                    null),
                Dinero.deCop(90000))
            .isEmpty());
  }

  @Test
  void sinPrecioDeLaPrendaNiDelSetNoHaySumaQueHacer() {
    assertTrue(
        ArmadorDeSets.armar(
                extraido(
                    "Falda",
                    TipoProductoProveedor.FALDA,
                    59900,
                    List.of(acompanante("Básica", 48000, PrendaAcompanante.BLUSA)),
                    null),
                null)
            .isEmpty());
  }

  @Test
  void losPreciosGuardadosAntesSinClaseNoArmanNada() {
    // Un borrador viejo releído: «Gorra» sin clase ni prenda.
    assertTrue(
        ArmadorDeSets.armar(
                extraido(
                    "Camiseta",
                    TipoProductoProveedor.CAMISETA,
                    55000,
                    List.of(new PrecioAdicional("Gorra", Dinero.deCop(35000))),
                    null),
                Dinero.deCop(55000))
            .isEmpty());
  }

  /** El agotado también se arma: su huella tiene que ser la del set para agotar el set. */
  @Test
  void unAgotadoTambienSeArma() {
    ProductoExtraido falda =
        extraido(
            "Falda",
            TipoProductoProveedor.FALDA,
            49900,
            List.of(combo("Set", 88000, PrendaAcompanante.BLUSA)),
            null);
    ProductoExtraido agotada =
        new ProductoExtraido(
            true,
            true,
            falda.titulo(),
            falda.linea(),
            falda.tipo(),
            falda.precioProveedor(),
            falda.tallas(),
            null,
            List.of(),
            null,
            null,
            null,
            false,
            falda.confianza(),
            null,
            null,
            null,
            falda.preciosAdicionales(),
            List.of(),
            null);

    SetArmado set = ArmadorDeSets.armar(agotada, Dinero.deCop(49900)).orElseThrow();

    assertTrue(set.producto().estaAgotado());
    assertEquals(PrendaAcompanante.BLUSA, set.armado().acompanante());
  }

  /**
   * La gorra arma set con la pantaloneta y la bermuda desde el 29 de octubre de 2026 (ADR-0101):
   * «Pantaloneta $39.990 / Gorra 35.000 / Dúo $69.900», con la palabra del proveedor. Con una
   * falda, no.
   */
  @Test
  void laPantalonetaConGorraEsUnDuoYLaFaldaConGorraNoArmaSet() {
    SetArmado duo =
        armar(
            extraido(
                "Pantaloneta con licra para caballero importado",
                TipoProductoProveedor.SHORT,
                39990,
                List.of(
                    acompanante("Gorra", 35000, PrendaAcompanante.GORRA),
                    combo("Dúo", 69900, PrendaAcompanante.GORRA)),
                null));

    assertEquals(Dinero.deCop(69900), duo.costo());
    assertEquals(TipoProductoProveedor.SHORT, duo.producto().tipo());
    assertEquals("Dúo pantaloneta con licra importado y gorra", duo.producto().titulo());
    assertTrue(
        ArmadorDeSets.armar(
                extraido(
                    "Falda",
                    TipoProductoProveedor.FALDA,
                    40000,
                    List.of(acompanante("Gorra", 35000, PrendaAcompanante.GORRA)),
                    null),
                Dinero.deCop(40000))
            .isEmpty());
  }

  /** «Set Blusa y falda» a un solo precio ya es el conjunto: no espera acompañante. */
  @Test
  void elConjuntoQueElAnuncioVendeEnteroNoEsperaAcompanante() {
    assertFalse(
        ArmadorDeSets.anunciaAcompanante(
            "Set Blusa y falda(VY3155) Talla S M L 💲94", TipoProductoProveedor.CONJUNTO_FALDA));
  }

  @Test
  void laPalabraDelComboEsLaDelProveedor() {
    assertEquals(Optional.of("Dúo"), ArmadorDeSets.palabraDelCombo("Dúo"));
    assertEquals(Optional.of("Dúo"), ArmadorDeSets.palabraDelCombo("duo 🧢👕"));
    assertEquals(Optional.of("Set"), ArmadorDeSets.palabraDelCombo("Set falda + básica"));
    assertEquals(Optional.of("Conjunto"), ArmadorDeSets.palabraDelCombo("Conjunto"));
    assertEquals(Optional.empty(), ArmadorDeSets.palabraDelCombo("Polo + gorra"));
  }

  /** Si el modelo repite la gorra, o hay dos gorras, se suma una: la del set es una. */
  @Test
  void unAcompananteRepetidoSeSumaUnaSolaVez() {
    SetArmado set =
        armar(
            extraido(
                "Camiseta oversize",
                TipoProductoProveedor.CAMISETA,
                55000,
                List.of(
                    acompanante("Gorras", 35000, PrendaAcompanante.GORRA),
                    acompanante("Gorra bordada", 40000, PrendaAcompanante.GORRA)),
                null));

    assertEquals(Dinero.deCop(90000), set.costo());
    assertEquals(List.of(Dinero.deCop(55000), Dinero.deCop(35000)), set.preciosLeidos());
  }

  /** Sin título no hay nombre para la prenda sola: no se muestra un «CAMISETA» crudo. */
  @Test
  void sinTituloNoSeMuestraLaPrendaSola() {
    SetArmado set =
        armar(
            extraido(
                null,
                TipoProductoProveedor.CAMISETA,
                55000,
                List.of(combo("Dúo", 85000, PrendaAcompanante.GORRA)),
                null));

    assertTrue(
        set.producto().preciosAdicionales().stream()
            .noneMatch(p -> p.clase() == ClaseDePrecioAdicional.PRENDA_SOLA));
  }

  // --- El costo contra el texto ---

  @Test
  void unPrecioEstaEscritoCompletoOEnMilesConLaPalabra() {
    String falda = "Falda 🤑$49.900\nSet, falda + básica $ 88.000";
    assertTrue(ArmadorDeSets.estaEscrito(Dinero.deCop(88000), falda));
    assertTrue(ArmadorDeSets.estaEscrito(Dinero.deCop(49900), falda));
    assertTrue(
        ArmadorDeSets.estaEscrito(
            Dinero.deCop(35000), "Nota: Las gorras tiene un valor de 35mil-"));
    assertTrue(ArmadorDeSets.estaEscrito(Dinero.deCop(35000), "gorra a 35 mil"));
    assertFalse(ArmadorDeSets.estaEscrito(Dinero.deCop(95000), falda), "no está");
    assertFalse(
        ArmadorDeSets.estaEscrito(Dinero.deCop(8000), falda), "ni como parte de otra cifra");
    assertFalse(ArmadorDeSets.estaEscrito(Dinero.deCop(88000), null));
  }

  @Test
  void elSetSabeSiSusPreciosEstanEnElAnuncio() {
    SetArmado set =
        armar(
            extraido(
                "Falda de dama",
                TipoProductoProveedor.FALDA,
                59900,
                List.of(acompanante("Básica", 48000, PrendaAcompanante.BLUSA)),
                null));

    assertTrue(set.preciosEscritosEn("Falda 🤑$59.900 Básica 48.000"));
    assertFalse(set.preciosEscritosEn("Falda 🤑$59.900"), "la básica no está escrita");
  }

  // --- El título ---

  @Test
  void elTituloQuitaElGeneroYBajaSoloLaPrimeraPalabra() {
    assertEquals(
        "Set camiseta estilo Superdry y gorra",
        TituloDeSet.componer("Camiseta estilo Superdry para caballero", PrendaAcompanante.GORRA));
    assertEquals(
        "Conjunto falda plisada y blusa",
        TituloDeSet.componer("Falda plisada para dama", PrendaAcompanante.BLUSA));
    assertNull(TituloDeSet.componer(null, PrendaAcompanante.GORRA));
    assertNull(TituloDeSet.componer("Camiseta", PrendaAcompanante.OTRA));
  }

  // --- La huella, el proveedor, el peso ---

  @Test
  void laHuellaDelSetNoEsLaDeLaPrendaSolaNiLaDeOtroAcompanante() {
    UUID proveedor = UUID.randomUUID();
    HuellaProveedor camiseta = HuellaProveedor.deReferencia(proveedor, "RV101860");

    HuellaProveedor conGorra = HuellaProveedor.deSet(camiseta, PrendaAcompanante.GORRA);

    assertNotEquals(camiseta, conGorra);
    assertNotEquals(conGorra, HuellaProveedor.deSet(camiseta, PrendaAcompanante.BLUSA));
    assertEquals(conGorra, HuellaProveedor.deSet(camiseta, PrendaAcompanante.GORRA));
  }

  @Test
  void soloArmaSetsQuienEntraPorLaExportacionDelChat() {
    Proveedor ropa =
        Proveedor.crear(
            "La Riverah",
            LineaCatalogo.ROPA,
            "+573019854097",
            "La Riverah",
            null,
            OrdenDePublicacion.TEXTO_PRIMERO);
    assertFalse(ropa.armaSets(), "nadie arma sets si no se le marca");
    ropa.definirArmaSets(true);
    assertTrue(ropa.armaSets());

    Proveedor tecnologia =
        Proveedor.crear(
            "Mayorista",
            LineaCatalogo.TECNOLOGIA,
            "+573000000000",
            "Mayorista",
            null,
            OrdenDePublicacion.TEXTO_PRIMERO);
    assertThrows(ExcepcionDeDominio.class, () -> tecnologia.definirArmaSets(true));
  }

  /** Lo arma la ingesta después de comparar con las exclusiones: excluirlo no haría nada. */
  @Test
  void elConjuntoConFaldaNoSeExcluye() {
    Proveedor riverah =
        Proveedor.crear(
            "La Riverah",
            LineaCatalogo.ROPA,
            "+573019854097",
            "La Riverah",
            null,
            OrdenDePublicacion.TEXTO_PRIMERO);

    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            riverah.definirTiposExcluidos(java.util.Set.of(TipoProductoProveedor.CONJUNTO_FALDA)));
  }

  @Test
  void unSetSoloSeArmaConBlusaOGorra() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new ArmadoDeSet(PrendaAcompanante.OTRA, OrigenDelCostoDeSet.ANUNCIADO));
  }

  // --- Lo que dejó la primera ingesta con sets (12 de octubre de 2026) ---

  private static final String FALDA_MALLATEX =
      "✅Falda de dama ✅Tela Mallatex americana ✅Talla  S M L Falda 🤑$49.900 "
          + "Set, falda + básica $ 88.000";

  /** El modelo restó el set y la falda, y puso una blusa a 38.100 que nadie escribió. */
  @Test
  void unPrecioAdicionalQueElTextoNoEscribeSeDescarta() {
    ProductoExtraido falda =
        extraido(
            "Falda de dama",
            TipoProductoProveedor.FALDA,
            49900,
            List.of(
                acompanante("Básica", 38100, PrendaAcompanante.BLUSA),
                combo("Set falda + básica", 88000, PrendaAcompanante.BLUSA)),
            TEXTOS);

    ProductoExtraido contrastada = falda.contrastadoCon(FALDA_MALLATEX);

    assertEquals(
        List.of(combo("Set falda + básica", 88000, PrendaAcompanante.BLUSA)),
        contrastada.preciosAdicionales());
  }

  @Test
  void losMilesPegadosAlSimboloCuentanComoEscritos() {
    assertTrue(PrecioEscrito.en(Dinero.deCop(124000), "Jean Mom Fit 💲124 Talla S M L"));
    assertFalse(PrecioEscrito.en(Dinero.deCop(12000), "Jean Mom Fit 💲124 Talla S M L"));
  }

  /**
   * Sin los textos del set, el nombre en inglés se arma, y sin descripción de la prenda se avisa.
   */
  @Test
  void unSetSinSusTextosArmaElNombreEnInglesYLoDice() {
    ProductoExtraido camiseta =
        new ProductoExtraido(
            true,
            false,
            "Camiseta oversize para caballero",
            LineaCatalogo.ROPA,
            TipoProductoProveedor.CAMISETA,
            Dinero.deCop(55000),
            Tallas.lista(List.of("S", "M")),
            null,
            List.of(),
            null,
            null,
            "Oversized T-shirt for men",
            false,
            new BigDecimal("0.9"),
            null,
            null,
            null,
            List.of(acompanante("Gorras", 35000, PrendaAcompanante.GORRA)),
            List.of(),
            null);

    SetArmado set = armar(camiseta);

    assertEquals("Oversized T-shirt and cap set", set.producto().altEn());
    assertNull(set.producto().descripcion());
    assertFalse(set.conDescripcionDelSet());
    assertTrue(
        armar(
                extraido(
                    "Falda",
                    TipoProductoProveedor.FALDA,
                    49900,
                    List.of(combo("Set", 88000, PrendaAcompanante.BLUSA)),
                    TEXTOS))
            .conDescripcionDelSet());
  }

  // --- La descripción que el modelo no escribió ---

  /**
   * Los dos anuncios de camisetas oversize de La Riverah del 12 de octubre de 2026: el modelo no
   * escribió la descripción del set, ni siquiera en el que leyó la gorra.
   */
  @Test
  void sinDescripcionDelSetLaDeLaCamisetaDiceQueVaConLaGorra() {
    assertEquals(
        "Camiseta oversize para caballero con cuello redondo. Se vende en set con la gorra que"
            + " aparece en las fotos.",
        ArmadorDeSets.describirSet(
            "Camiseta oversize para caballero con cuello redondo.", PrendaAcompanante.GORRA));
  }

  @Test
  void sinDescripcionDelSetLaDeLaFaldaDiceQueVaConLaBlusa() {
    SetArmado set =
        armar(
            extraido(
                "Falda de dama",
                TipoProductoProveedor.FALDA,
                59900,
                List.of(acompanante("Básica", 48000, PrendaAcompanante.BLUSA)),
                null));

    assertEquals(
        "La prenda sola. Se vende en conjunto con la blusa que aparece en las fotos.",
        set.producto().descripcion());
    assertTrue(set.conDescripcionDelSet());
  }

  @Test
  void laFraseSeSeparaConPuntoAunqueLaPrendaNoLoTraiga() {
    assertEquals(
        "Camiseta oversize. Se vende en set con la gorra que aparece en las fotos.",
        ArmadorDeSets.describirSet("  Camiseta oversize  ", PrendaAcompanante.GORRA));
  }

  @Test
  void unaDescripcionQueYaNombraElAcompananteNoSeRepite() {
    assertEquals(
        "Camiseta oversize con su Gorra a juego.",
        ArmadorDeSets.describirSet(
            "Camiseta oversize con su Gorra a juego.", PrendaAcompanante.GORRA));
    assertEquals(
        "Falda plisada con su básica.",
        ArmadorDeSets.describirSet("Falda plisada con su básica.", PrendaAcompanante.BLUSA));
    assertEquals(
        "Falda plisada con blusa.",
        ArmadorDeSets.describirSet("Falda plisada con blusa.", PrendaAcompanante.BLUSA));
  }

  @Test
  void laDescripcionDelSetQueEscribioElModeloNoSeToca() {
    SetArmado set =
        armar(
            extraido(
                "Falda de dama",
                TipoProductoProveedor.FALDA,
                59900,
                List.of(acompanante("Básica", 48000, PrendaAcompanante.BLUSA)),
                TEXTOS));

    assertEquals("Falda con la blusa que aparece en las fotos.", set.producto().descripcion());
  }

  @Test
  void sinDescripcionDeLaPrendaNoHayNadaQueCompletar() {
    assertNull(ArmadorDeSets.describirSet(null, PrendaAcompanante.GORRA));
    assertNull(ArmadorDeSets.describirSet("  ", PrendaAcompanante.BLUSA));
  }

  @Test
  void elTextoQueOfreceUnAcompananteSeReconoce() {
    assertTrue(
        ArmadorDeSets.anunciaAcompanante(
            "Camiseta 🎽55.000~~ Promo 4x200.000 Nota: Las gorras tiene un valor de 35mil-",
            TipoProductoProveedor.CAMISETA));
    assertTrue(
        ArmadorDeSets.anunciaAcompanante(
            "Falda 🤑$59.900 Básica 48.000", TipoProductoProveedor.FALDA));
    assertFalse(
        ArmadorDeSets.anunciaAcompanante(
            "BÁSICA RAYA AMERICANAS ❤️TELA Licrada $49.900 Promo 2x88.000",
            TipoProductoProveedor.CAMISETA),
        "la básica es la prenda misma");
    assertFalse(
        ArmadorDeSets.anunciaAcompanante(
            "JEANS LICRADO CABALLERO Tallas 30 a la 36 $49.000", TipoProductoProveedor.PANTALON));
  }
}
