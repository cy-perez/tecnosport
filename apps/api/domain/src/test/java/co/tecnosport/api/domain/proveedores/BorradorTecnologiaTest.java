package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BorradorTecnologiaTest {

  private static final UUID PROVEEDOR = UUID.fromString("018f0000-0000-7000-8000-000000000001");
  private static final Instant LUNES = Instant.parse("2026-10-05T05:00:00Z");
  private static final Instant JUEVES = Instant.parse("2026-10-08T05:00:00Z");
  private static final MargenMinimo MARGEN = MargenMinimo.deTecnologia(Dinero.deCop(400_000));

  private static ModeloDeLista a17(List<String> paleta) {
    return new ModeloDeLista(
        "samsung-galaxy-a17-5g",
        "Samsung Galaxy A17 5G",
        "Samsung",
        "celulares",
        "El Galaxy A17 5G.",
        "El Galaxy A17 5G.",
        paleta);
  }

  private static ConfiguracionTecnologia config(String sku, long costo) {
    return ConfiguracionTecnologia.deLista(
        sku, "Galaxy A17 " + sku, "8GB", "256GB", "1 SIM", Dinero.deCop(costo), null, List.of());
  }

  private static BorradorTecnologia nuevo() {
    return BorradorTecnologia.nuevo(
        PROVEEDOR,
        a17(List.of("Negro", "Gris", "Azul")),
        List.of(config("a17-256", 675_000), config("a17-128", 600_000)),
        null,
        LUNES,
        LUNES);
  }

  @Test
  void laHuellaEsDelModeloYNoCambiaConElCosto() {
    BorradorTecnologia borrador = nuevo();
    assertEquals(HuellaProveedor.deModelo(PROVEEDOR, "samsung-galaxy-a17-5g"), borrador.huella());
    assertNotEquals(
        HuellaProveedor.deModelo(PROVEEDOR, "samsung-galaxy-a17-5g"),
        HuellaProveedor.deModelo(UUID.randomUUID(), "samsung-galaxy-a17-5g"));
  }

  @Test
  void unaListaNuevaCambiaLosCostosYConservaLaEleccion() {
    BorradorTecnologia borrador = nuevo();
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(849_900))));

    borrador.actualizarConLista(
        a17(List.of("Negro", "Gris", "Azul")), List.of(config("a17-256", 660_000)), JUEVES);

    ConfiguracionTecnologia c = borrador.configuraciones().getFirst();
    assertEquals(1, borrador.configuraciones().size(), "la de 128 ya no viene");
    assertEquals(Dinero.deCop(660_000), c.costoProveedor());
    assertEquals(List.of("Negro"), c.coloresElegidos());
    assertEquals(Dinero.deCop(849_900), c.precioVenta());
    assertEquals(JUEVES, borrador.vistoEn());
  }

  @Test
  void unaListaMasViejaNoPisaLaDeHoy() {
    BorradorTecnologia borrador =
        BorradorTecnologia.nuevo(
            PROVEEDOR, a17(List.of()), List.of(config("a17-256", 660_000)), null, JUEVES, JUEVES);

    borrador.actualizarConLista(a17(List.of()), List.of(config("a17-256", 700_000)), LUNES);

    assertEquals(Dinero.deCop(660_000), borrador.configuraciones().getFirst().costoProveedor());
  }

  @Test
  void conPaletaUnColorTieneQueSerDeElla() {
    BorradorTecnologia borrador = nuevo();
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            borrador.elegir(
                List.of(
                    new BorradorTecnologia.Eleccion(
                        "a17-256", List.of("Rosado"), Dinero.deCop(849_900)))));
  }

  @Test
  void sinPaletaElColorSeEscribeAMano() {
    BorradorTecnologia borrador =
        BorradorTecnologia.nuevo(
            PROVEEDOR, a17(List.of()), List.of(config("a17-256", 660_000)), null, LUNES, LUNES);
    borrador.elegir(
        List.of(new BorradorTecnologia.Eleccion("a17-256", List.of(" Rosado "), Dinero.deCop(1))));
    assertEquals(List.of("Rosado"), borrador.configuraciones().getFirst().coloresElegidos());
  }

  @Test
  void noSeApruebaSinNadaQueVenderNiConUnPrecioPendiente() {
    BorradorTecnologia borrador = conCuatroFotos(nuevo());
    UUID producto = UUID.randomUUID();
    assertThrows(ExcepcionDeDominio.class, () -> borrador.aprobar(producto, MARGEN));

    borrador.elegir(List.of(new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), null)));
    assertThrows(ExcepcionDeDominio.class, () -> borrador.aprobar(producto, MARGEN));

    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(849_900))));
    borrador.aprobar(producto, MARGEN);
    assertEquals(EstadoBorrador.APROBADO, borrador.estado());
    assertEquals(producto, borrador.productoId().orElseThrow());
  }

  /**
   * Revisión adversarial del 27 de octubre de 2026: bastaba superar el costo. Ahora hace falta el
   * margen mínimo de tecnología —el 10 % sobre el costo, con el tope de ganancia—, medido sobre el
   * precio de contado.
   */
  @Test
  void noSeApruebaConMenosDelMargenMinimo() {
    BorradorTecnologia borrador = conCuatroFotos(nuevo());
    // Costo 675.000: 740.000 le gana un 9,6 %.
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(740_000))));
    assertThrows(
        PrecioSinMargenMinimoException.class, () -> borrador.aprobar(UUID.randomUUID(), MARGEN));

    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(742_500))));
    borrador.aprobar(UUID.randomUUID(), MARGEN);
    assertEquals(EstadoBorrador.APROBADO, borrador.estado());
  }

  /** El margen que recibe es el que manda: con un tope de 50.000, el 10 % de 675.000 no aplica. */
  @Test
  void elTopeDeGananciaBajaElMinimoQueSeExige() {
    BorradorTecnologia borrador = conCuatroFotos(nuevo());
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(725_000))));
    assertThrows(PrecioSinMargenMinimoException.class, () -> borrador.exigirAprobable(MARGEN));

    borrador.aprobar(UUID.randomUUID(), MargenMinimo.deTecnologia(Dinero.deCop(50_000)));
    assertEquals(EstadoBorrador.APROBADO, borrador.estado());
  }

  @Test
  void loQueQuedoSinColoresAlAprobarYaEstaDecidido() {
    BorradorTecnologia borrador = conCuatroFotos(nuevo());
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(849_900))));
    assertEquals(Set.of(), borrador.skusDescartados(), "en revisión no hay nada decidido");

    borrador.aprobar(UUID.randomUUID(), MARGEN);

    assertEquals(Set.of("a17-128"), borrador.skusDescartados());
  }

  @Test
  void unRechazadoDescartaTodasSusConfiguraciones() {
    BorradorTecnologia borrador = nuevo();
    assertThrows(ExcepcionDeDominio.class, () -> borrador.rechazar(" "));
    borrador.rechazar("No vendemos esta gama");
    assertEquals(Set.of("a17-256", "a17-128"), borrador.skusDescartados());
    assertThrows(ExcepcionDeDominio.class, () -> borrador.elegir(List.of()));
  }

  @Test
  void elCostoDelProductoEsElMasBajoDeLoQueSeVende() {
    BorradorTecnologia borrador = nuevo();
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(849_900))));
    // La de 128 cuesta menos, pero no se vende.
    assertEquals(Dinero.deCop(675_000), borrador.costoMinimoDeLoQueSeVende());
  }

  @Test
  void unaConfiguracionRepetidaNoEntra() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            BorradorTecnologia.nuevo(
                PROVEEDOR,
                a17(List.of()),
                List.of(config("a17-256", 1), config("a17-256", 2)),
                null,
                LUNES,
                LUNES));
  }

  @Test
  void elSkuDeLaVarianteCabeEsEstableYDistingueElColor() {
    ConfiguracionTecnologia larga =
        config("motorola-edge-50-fusion-5g-8gb-ram-256gb-sim-esim", 1_000_000);
    String negro = larga.skuDeVariante("Negro Obsidiana (Obsidian Black)");

    assertTrue(negro.length() <= 60, negro);
    assertTrue(negro.startsWith("MOTOROLA-EDGE-50-FUSION"), negro);
    assertEquals(negro, larga.skuDeVariante(" negro obsidiana (obsidian black)"));
    assertNotEquals(negro, larga.skuDeVariante("Azul"));
    assertNotEquals(
        negro,
        config("motorola-edge-50-fusion-5g-8gb-ram-256gb-sim-esim-2", 1)
            .skuDeVariante("Negro Obsidiana (Obsidian Black)"));
  }

  @Test
  void elCostoDeUnaListaViejaNoPisaElDeHoy() {
    VarianteDeProveedor hoy =
        new VarianteDeProveedor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            PROVEEDOR,
            "a17-256",
            "Negro",
            Dinero.deCop(660_000),
            JUEVES);
    assertEquals(hoy, hoy.conCosto(Dinero.deCop(700_000), LUNES));
    assertEquals(
        Dinero.deCop(650_000),
        hoy.conCosto(Dinero.deCop(650_000), JUEVES.plusSeconds(86_400)).costo());
  }

  @Test
  void noSeApruebaAlCostoNiPorDebajo() {
    BorradorTecnologia borrador = nuevo();
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(67_500))));
    assertThrows(ExcepcionDeDominio.class, () -> borrador.aprobar(UUID.randomUUID(), MARGEN));

    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(675_000))));
    assertThrows(ExcepcionDeDominio.class, () -> borrador.aprobar(UUID.randomUUID(), MARGEN));
  }

  @Test
  void unColorEscritoDosVecesConOtrasMayusculasEsUnoSolo() {
    BorradorTecnologia borrador =
        BorradorTecnologia.nuevo(
            PROVEEDOR, a17(List.of()), List.of(config("a17-256", 660_000)), null, LUNES, LUNES);
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion(
                "a17-256", List.of("Negro", " negro", "NEGRO"), Dinero.deCop(849_900))));
    assertEquals(List.of("Negro"), borrador.configuraciones().getFirst().coloresElegidos());
  }

  @Test
  void unColorQueSalioDeLaPaletaSeSueltaDeLaEleccion() {
    BorradorTecnologia borrador = nuevo();
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion(
                "a17-256", List.of("Negro", "Gris"), Dinero.deCop(849_900))));

    borrador.actualizarConLista(
        a17(List.of("Negro", "Azul")), List.of(config("a17-256", 660_000)), JUEVES);

    assertEquals(List.of("Negro"), borrador.configuraciones().getFirst().coloresElegidos());
  }

  @Test
  void unDatoMasLargoQueSuColumnaSeRechazaConMotivo() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            ConfiguracionTecnologia.deLista(
                "a17",
                "Galaxy",
                "8GB",
                "256GB",
                "1 SIM",
                Dinero.deCop(1),
                null,
                List.of("x".repeat(81))));
  }

  // --- Las fotos (ADR-0079) -----------------------------------------------------------------

  /** Un SHA-256 bien formado y distinto por etiqueta. */
  private static String hex(String etiqueta) {
    return String.format("%064x", new java.math.BigInteger(1, etiqueta.getBytes()));
  }

  private static FotoDeBorradorTecnologia foto(String etiqueta) {
    String hash = hex(etiqueta);
    return new FotoDeBorradorTecnologia(
        UUID.randomUUID(),
        List.of(
            new FotoDeBorradorTecnologia.Variante(1200, "b/" + hash + "-1200.avif", 58_000),
            new FotoDeBorradorTecnologia.Variante(480, "b/" + hash + "-480.avif", 12_000)),
        "b/" + hash + ".jpg",
        1200,
        hash,
        "Galaxy A17 sobre fondo blanco",
        "Galaxy A17 on a white background",
        LUNES);
  }

  private static BorradorTecnologia conCuatroFotos(BorradorTecnologia borrador) {
    for (int i = 0; i < BorradorTecnologia.MINIMO_DE_FOTOS; i++) {
      borrador.agregarFoto(foto("h" + i));
    }
    return borrador;
  }

  @Test
  void unModeloNuevoNoSeApruebaSinSusCuatroFotos() {
    BorradorTecnologia borrador = nuevo();
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(849_900))));
    for (int i = 0; i < BorradorTecnologia.MINIMO_DE_FOTOS - 1; i++) {
      borrador.agregarFoto(foto("h" + i));
    }

    ExcepcionDeDominio error =
        assertThrows(ExcepcionDeDominio.class, () -> borrador.aprobar(UUID.randomUUID(), MARGEN));
    assertTrue(error.getMessage().contains("tiene 3"), error.getMessage());

    borrador.agregarFoto(foto("h3"));
    borrador.aprobar(UUID.randomUUID(), MARGEN);
    assertEquals(EstadoBorrador.APROBADO, borrador.estado());
  }

  @Test
  void conExcepcionDeFotosSeApruebaConLasQueDeclara() {
    // Decisión del 28/10/2026: los AirPods Pro 3 no tienen más de dos tomas oficiales.
    BorradorTecnologia borrador = nuevo();
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(849_900))));
    borrador.agregarFoto(foto("h0"));
    borrador.declararExcepcionDeFotos(new ExcepcionDeFotos(2, "no hay más fotos oficiales"));

    ExcepcionDeDominio error =
        assertThrows(ExcepcionDeDominio.class, () -> borrador.aprobar(UUID.randomUUID(), MARGEN));
    assertTrue(error.getMessage().contains("necesita 2"), error.getMessage());
    assertEquals(1, borrador.fotosQueFaltan());

    borrador.agregarFoto(foto("h1"));
    assertEquals(0, borrador.fotosQueFaltan());
    borrador.aprobar(UUID.randomUUID(), MARGEN);
    assertEquals(EstadoBorrador.APROBADO, borrador.estado());
  }

  @Test
  void quitarLaExcepcionVuelveAPedirLasCuatro() {
    BorradorTecnologia borrador = nuevo();
    borrador.declararExcepcionDeFotos(new ExcepcionDeFotos(2, "no hay más fotos oficiales"));
    borrador.agregarFoto(foto("h0"));
    borrador.agregarFoto(foto("h1"));
    assertEquals(0, borrador.fotosQueFaltan());

    borrador.declararExcepcionDeFotos(null);

    assertEquals(BorradorTecnologia.MINIMO_DE_FOTOS - 2, borrador.fotosQueFaltan());
    assertTrue(borrador.excepcionDeFotos().isEmpty());
  }

  @Test
  void laExcepcionSoloBajaElMinimoYDicePorQue() {
    assertThrows(ExcepcionDeDominio.class, () -> new ExcepcionDeFotos(0, "sin fotos"));
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new ExcepcionDeFotos(BorradorTecnologia.MINIMO_DE_FOTOS, "las de siempre"));
    assertThrows(ExcepcionDeDominio.class, () -> new ExcepcionDeFotos(2, "  "));
    assertEquals("no hay más", new ExcepcionDeFotos(3, "  no hay más ").motivo());
  }

  @Test
  void unBorradorAprobadoNoCambiaSuExcepcion() {
    BorradorTecnologia borrador = conCuatroFotos(nuevo());
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(849_900))));
    borrador.aprobar(UUID.randomUUID(), MARGEN);

    assertThrows(
        ExcepcionDeDominio.class,
        () -> borrador.declararExcepcionDeFotos(new ExcepcionDeFotos(2, "no hay más")));
  }

  @Test
  void lasConfiguracionesNuevasDeUnProductoNoPidenFotos() {
    BorradorTecnologia borrador =
        BorradorTecnologia.nuevo(
            PROVEEDOR,
            a17(List.of("Negro")),
            List.of(config("a17-512", 900_000)),
            UUID.randomUUID(),
            LUNES,
            LUNES);
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-512", List.of("Negro"), Dinero.deCop(1_049_900))));

    borrador.aprobar(borrador.productoId().orElseThrow(), MARGEN);

    assertEquals(EstadoBorrador.APROBADO, borrador.estado());
  }

  @Test
  void lasFotosQuedanEnElOrdenEnQueEntranYLaPrimeraEsLaPrincipal() {
    BorradorTecnologia borrador = nuevo();
    borrador.agregarFoto(foto("a"));
    borrador.agregarFoto(foto("b"));

    assertEquals(
        List.of(hex("a"), hex("b")), borrador.fotos().stream().map(f -> f.hash()).toList());
    assertEquals(1200, borrador.fotos().getFirst().mayor().ancho());
  }

  @Test
  void laMismaFotoDosVecesNoEntra() {
    BorradorTecnologia borrador = nuevo();
    borrador.agregarFoto(foto("a"));

    assertThrows(ExcepcionDeDominio.class, () -> borrador.agregarFoto(foto("a")));
    assertEquals(1, borrador.fotos().size());
  }

  @Test
  void noCabenMasFotosQueLaPrincipalYLaGaleria() {
    BorradorTecnologia borrador = nuevo();
    for (int i = 0; i < BorradorTecnologia.TOPE_DE_FOTOS; i++) {
      borrador.agregarFoto(foto("f" + i));
    }

    assertThrows(ExcepcionDeDominio.class, () -> borrador.agregarFoto(foto("otra")));
  }

  @Test
  void quitarUnaFotoLaDevuelveParaBorrarSusArchivos() {
    BorradorTecnologia borrador = nuevo();
    FotoDeBorradorTecnologia a = foto("a");
    borrador.agregarFoto(a);
    borrador.agregarFoto(foto("b"));

    FotoDeBorradorTecnologia quitada = borrador.quitarFoto(a.id());

    assertEquals(
        List.of(
            "b/" + hex("a") + "-1200.avif",
            "b/" + hex("a") + "-480.avif",
            "b/" + hex("a") + ".jpg"),
        quitada.referencias());
    assertEquals(List.of(hex("b")), borrador.fotos().stream().map(f -> f.hash()).toList());
    assertThrows(ExcepcionDeDominio.class, () -> borrador.quitarFoto(UUID.randomUUID()));
  }

  @Test
  void unBorradorRechazadoNoRecibeFotos() {
    BorradorTecnologia borrador = nuevo();
    borrador.rechazar("No vendemos esta gama");

    assertThrows(ExcepcionDeDominio.class, () -> borrador.agregarFoto(foto("a")));
  }

  @Test
  void unaFotoNoTraeDosVecesElMismoAncho() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new FotoDeBorradorTecnologia(
                UUID.randomUUID(),
                List.of(
                    new FotoDeBorradorTecnologia.Variante(1200, "x.avif", 1),
                    new FotoDeBorradorTecnologia.Variante(1200, "y.avif", 1)),
                null,
                1200,
                hex("h"),
                "alt",
                "alt",
                LUNES));
  }

  @Test
  void otraListaConservaLasFotos() {
    BorradorTecnologia borrador = nuevo();
    borrador.agregarFoto(foto("a"));

    borrador.actualizarConLista(
        a17(List.of("Negro", "Gris", "Azul")), List.of(config("a17-256", 660_000)), JUEVES);

    assertEquals(1, borrador.fotos().size());
  }

  @Test
  void elHashDeUnaFotoEsUnSha256EnMinusculas() {
    FotoDeBorradorTecnologia foto = foto("a");
    FotoDeBorradorTecnologia enMayusculas =
        new FotoDeBorradorTecnologia(
            UUID.randomUUID(),
            foto.variantes(),
            null,
            1200,
            foto.hash().toUpperCase(),
            "alt",
            "alt",
            LUNES);
    assertEquals(foto.hash(), enMayusculas.hash());

    BorradorTecnologia borrador = nuevo();
    borrador.agregarFoto(foto);
    assertThrows(ExcepcionDeDominio.class, () -> borrador.agregarFoto(enMayusculas));
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new FotoDeBorradorTecnologia(
                UUID.randomUUID(), foto.variantes(), null, 1200, "no-es-un-hash", "a", "a", LUNES));
  }

  @Test
  void elBorradorDiceCuantasFotosLeFaltan() {
    BorradorTecnologia borrador = nuevo();
    borrador.agregarFoto(foto("a"));
    assertEquals(BorradorTecnologia.MINIMO_DE_FOTOS - 1, borrador.fotosQueFaltan());
  }

  @Test
  void reordenarDejaLaPrimeraComoPrincipal() {
    BorradorTecnologia borrador = nuevo();
    FotoDeBorradorTecnologia a = foto("a");
    FotoDeBorradorTecnologia b = foto("b");
    FotoDeBorradorTecnologia c = foto("c");
    borrador.agregarFoto(a);
    borrador.agregarFoto(b);
    borrador.agregarFoto(c);

    borrador.reordenarFotos(List.of(c.id(), a.id(), b.id()));

    assertEquals(
        List.of(c.id(), a.id(), b.id()), borrador.fotos().stream().map(f -> f.id()).toList());
  }

  @Test
  void unOrdenQueNoNombraCadaFotoUnaVezNoSeAplica() {
    BorradorTecnologia borrador = nuevo();
    FotoDeBorradorTecnologia a = foto("a");
    FotoDeBorradorTecnologia b = foto("b");
    borrador.agregarFoto(a);
    borrador.agregarFoto(b);

    for (List<UUID> orden :
        List.of(
            List.of(a.id()),
            List.of(a.id(), a.id()),
            List.of(a.id(), b.id(), UUID.randomUUID()),
            List.of(a.id(), UUID.randomUUID()))) {
      assertThrows(ExcepcionDeDominio.class, () -> borrador.reordenarFotos(orden));
    }
    assertEquals(List.of(a.id(), b.id()), borrador.fotos().stream().map(f -> f.id()).toList());
  }

  @Test
  void unBorradorRechazadoNoSeReordena() {
    BorradorTecnologia borrador = nuevo();
    FotoDeBorradorTecnologia a = foto("a");
    borrador.agregarFoto(a);
    borrador.rechazar("No vendemos esta gama");

    assertThrows(ExcepcionDeDominio.class, () -> borrador.reordenarFotos(List.of(a.id())));
  }

  @Test
  void laFichaEditadaEsLaQueSePublica() {
    BorradorTecnologia borrador = nuevo();
    assertEquals("El Galaxy A17 5G.", borrador.descripcion());

    borrador.editarDescripcion("  El Galaxy A17 5G, sin la nota del proveedor.  ");

    assertEquals("El Galaxy A17 5G, sin la nota del proveedor.", borrador.descripcion());
    assertEquals("El Galaxy A17 5G.", borrador.modelo().descripcion(), "la de la skill no cambia");
  }

  @Test
  void enBlancoLaFichaVuelveALaDeLaSkill() {
    BorradorTecnologia borrador = nuevo();
    borrador.editarDescripcion("Otra ficha.");

    borrador.editarDescripcion("   ");

    assertTrue(borrador.descripcionEditada().isEmpty());
    assertEquals("El Galaxy A17 5G.", borrador.descripcion());
  }

  @Test
  void unaFichaConMarcadorNoSeGuarda() {
    BorradorTecnologia borrador = nuevo();

    assertThrows(
        ExcepcionDeDominio.class, () -> borrador.editarDescripcion("Garantía de [[PLAZO]] meses."));
    assertTrue(borrador.descripcionEditada().isEmpty());
  }

  @Test
  void unaListaNuevaNoPisaLaFichaEditadaYAvisaSiTrajoOtra() {
    BorradorTecnologia borrador = nuevo();
    borrador.editarDescripcion("Editada a mano.");

    borrador.actualizarConLista(
        new ModeloDeLista(
            "samsung-galaxy-a17-5g",
            "Samsung Galaxy A17 5G",
            "Samsung",
            "celulares",
            "La ficha nueva de la skill.",
            null,
            List.of("Negro", "Gris", "Azul")),
        List.of(config("a17-256", 660_000)),
        JUEVES);

    assertEquals("Editada a mano.", borrador.descripcion());
    assertEquals("La ficha nueva de la skill.", borrador.modelo().descripcion());
    assertTrue(borrador.fichaNuevaDeLaSkill());

    borrador.editarDescripcion(null);
    assertEquals("La ficha nueva de la skill.", borrador.descripcion());
    assertFalse(borrador.fichaNuevaDeLaSkill(), "volver a la de la skill apaga el aviso");
  }

  @Test
  void unaListaConLaMismaFichaNoAvisaNadaNiSinEdicion() {
    BorradorTecnologia editado = nuevo();
    editado.editarDescripcion("Editada a mano.");
    editado.actualizarConLista(
        a17(List.of("Negro", "Gris", "Azul")), List.of(config("a17-256", 660_000)), JUEVES);
    assertFalse(editado.fichaNuevaDeLaSkill());

    BorradorTecnologia sinEditar = nuevo();
    sinEditar.actualizarConLista(
        new ModeloDeLista(
            "samsung-galaxy-a17-5g",
            "Samsung Galaxy A17 5G",
            "Samsung",
            "celulares",
            "Otra.",
            null,
            List.of()),
        List.of(config("a17-256", 660_000)),
        JUEVES);
    assertFalse(sinEditar.fichaNuevaDeLaSkill());
    assertEquals("Otra.", sinEditar.descripcion());
  }

  @Test
  void unBorradorAprobadoNoSeEditaLaFicha() {
    BorradorTecnologia borrador = conCuatroFotos(nuevo());
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(849_900))));
    borrador.aprobar(UUID.randomUUID(), MARGEN);

    assertThrows(ExcepcionDeDominio.class, () -> borrador.editarDescripcion("Otra."));
  }
}
