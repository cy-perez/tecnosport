package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    BorradorTecnologia borrador = nuevo();
    UUID producto = UUID.randomUUID();
    assertThrows(ExcepcionDeDominio.class, () -> borrador.aprobar(producto));

    borrador.elegir(List.of(new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), null)));
    assertThrows(ExcepcionDeDominio.class, () -> borrador.aprobar(producto));

    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(849_900))));
    borrador.aprobar(producto);
    assertEquals(EstadoBorrador.APROBADO, borrador.estado());
    assertEquals(producto, borrador.productoId().orElseThrow());
  }

  @Test
  void loQueQuedoSinColoresAlAprobarYaEstaDecidido() {
    BorradorTecnologia borrador = nuevo();
    borrador.elegir(
        List.of(
            new BorradorTecnologia.Eleccion("a17-256", List.of("Negro"), Dinero.deCop(849_900))));
    assertEquals(Set.of(), borrador.skusDescartados(), "en revisión no hay nada decidido");

    borrador.aprobar(UUID.randomUUID());

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
}
