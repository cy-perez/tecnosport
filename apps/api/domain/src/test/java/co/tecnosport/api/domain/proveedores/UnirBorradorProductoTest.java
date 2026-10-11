package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * La camiseta de seda fría de La Riverah (10 de octubre de 2026): RV102517 crema, RV102516 café y
 * RV102515 negro, el mismo diseño con un SKU por color. El álbum da un borrador por SKU, y quien
 * revisa los une (camino acordado el 11 de octubre de 2026).
 */
class UnirBorradorProductoTest {

  private static final Instant T = Instant.parse("2026-10-10T19:00:00Z");
  private static final UUID PUBLICACION = UUID.randomUUID();
  private static final UUID PROVEEDOR = UUID.randomUUID();
  private static final UUID CREMA = UUID.randomUUID();
  private static final UUID CAFE = UUID.randomUUID();
  private static final UUID MONASTERY = UUID.randomUUID();
  private static final List<UUID> PUBLICADAS = List.of(CREMA, CAFE, MONASTERY);

  private static BorradorProducto delAlbum(
      String sku, UUID suFoto, String color, List<String> tallas, String diseno) {
    ProductoExtraido camiseta =
        new ProductoExtraido(
            true,
            false,
            "Camiseta seda fría para caballero",
            LineaCatalogo.ROPA,
            TipoProductoProveedor.CAMISETA,
            Dinero.deCop(35000),
            Tallas.lista(tallas),
            null,
            List.of(),
            "seda fría",
            "Camiseta de seda fría.",
            null,
            false,
            new BigDecimal("0.9"),
            null,
            sku);
    Set<UUID> ajenas = new java.util.HashSet<>(PUBLICADAS);
    ajenas.remove(suFoto);
    return BorradorProducto.nuevo(
        PUBLICACION,
        PROVEEDOR,
        camiseta,
        "{}",
        Dinero.deCop(35000),
        Dinero.deCop(55000),
        HuellaProveedor.deReferencia(PROVEEDOR, sku),
        null,
        Set.of(),
        new FotosDelProducto(ajenas, Map.of(suFoto, color), "{}", diseno),
        T);
  }

  @Test
  void dosDeLaMismaEtiquetaSonPosiblementeElMismoDiseno() {
    BorradorProducto crema = delAlbum("RV102517", CREMA, "crema", List.of("L", "XL"), "ax franja");
    BorradorProducto cafe = delAlbum("RV102516", CAFE, "café", List.of("L", "XL"), "AX franja");
    BorradorProducto monastery =
        delAlbum("RV102495", MONASTERY, "verde", List.of("M"), "monastery letras");

    assertEquals(Optional.of("ax franja"), crema.diseno());
    assertTrue(crema.mismoDisenoQue(cafe));
    assertFalse(crema.mismoDisenoQue(monastery));
  }

  @Test
  void unirSeQuedaConLasFotosYLosTonosDeLosDos() {
    BorradorProducto crema = delAlbum("RV102517", CREMA, "crema", List.of("L", "XL"), "ax franja");
    BorradorProducto cafe = delAlbum("RV102516", CAFE, "café", List.of("L", "XL"), "ax franja");

    crema.absorber(cafe, PUBLICADAS);

    assertEquals(Set.of(MONASTERY), crema.fotosDescartadas(), "la del café ya es suya");
    assertEquals(Map.of(CREMA, "crema", CAFE, "café"), crema.tonosSugeridos());
    assertEquals(List.of("crema", "café"), crema.tonosNombrados());
    assertEquals(Optional.of(2), crema.cantidadTonos());
    assertTrue(crema.tallasPorTono().estaVacia(), "las mismas tallas en los dos colores");
  }

  /** El crema viene en L y XL y el negro solo en M: no se publica un negro L que no existe. */
  @Test
  void conTallasDistintasGuardaLasDeCadaTono() {
    BorradorProducto crema = delAlbum("RV102517", CREMA, "crema", List.of("L", "XL"), "ax franja");
    BorradorProducto negro = delAlbum("RV102515", CAFE, "negro", List.of("M"), "ax franja");

    crema.absorber(negro, PUBLICADAS);

    assertEquals(List.of("L", "XL", "M"), crema.tallas().valores());
    assertEquals(Optional.of(List.of("L", "XL")), crema.tallasPorTono().tallasDe("crema"));
    assertEquals(Optional.of(List.of("M")), crema.tallasPorTono().tallasDe("negro"));
  }

  @Test
  void soloSeUnenDosEnRevisionDeLaMismaPublicacionYSinFotosDelPanel() {
    BorradorProducto crema = delAlbum("RV102517", CREMA, "crema", List.of("L"), "ax franja");

    assertThrows(ExcepcionDeDominio.class, () -> crema.absorber(crema, PUBLICADAS));

    BorradorProducto rechazado = delAlbum("RV102516", CAFE, "café", List.of("L"), "ax franja");
    rechazado.rechazar("no lo vendemos");
    assertThrows(ExcepcionDeDominio.class, () -> crema.absorber(rechazado, PUBLICADAS));

    BorradorProducto conSubida = delAlbum("RV102516", CAFE, "café", List.of("L"), "ax franja");
    conSubida.agregarFotoSubida(new FotoSubida(UUID.randomUUID(), "proveedores/x/foto.jpg", T));
    assertThrows(ExcepcionDeDominio.class, () -> crema.absorber(conSubida, PUBLICADAS));
  }

  /** Quien parte dice que no son el mismo: el panel no le sugiere volver a unirlos. */
  @Test
  void partirNoHeredaLaEtiquetaDeDiseno() {
    BorradorProducto crema = delAlbum("RV102517", CREMA, "crema", List.of("L"), "ax franja");
    crema.absorber(delAlbum("RV102516", CAFE, "café", List.of("L"), "ax franja"), PUBLICADAS);

    BorradorProducto partido = crema.partir(List.of(CAFE), PUBLICADAS, T);

    assertEquals(Optional.empty(), partido.diseno());
    assertFalse(crema.mismoDisenoQue(partido));
  }

  /** Las tallas por tono que el otro ya traía no se pierden al unirlo. */
  @Test
  void lasTallasPorTonoDelOtroSeConservan() {
    BorradorProducto crema = delAlbum("RV102517", CREMA, "crema", List.of("L", "XL"), "ax franja");
    ProductoExtraido conTallasPorTono =
        new ProductoExtraido(
            true,
            false,
            "Camiseta seda fría para caballero",
            LineaCatalogo.ROPA,
            TipoProductoProveedor.CAMISETA,
            Dinero.deCop(35000),
            Tallas.lista(List.of("M", "L")),
            2,
            List.of("café", "negro"),
            null,
            "Camiseta.",
            null,
            false,
            new BigDecimal("0.9"),
            null,
            "RV102516",
            new TallasPorTono(
                List.of(
                    new TallasPorTono.TallasDeUnTono("café", List.of("M")),
                    new TallasPorTono.TallasDeUnTono("negro", List.of("M", "L")))),
            null);
    BorradorProducto otro =
        BorradorProducto.nuevo(
            PUBLICACION,
            PROVEEDOR,
            conTallasPorTono,
            "{}",
            Dinero.deCop(35000),
            Dinero.deCop(55000),
            HuellaProveedor.deReferencia(PROVEEDOR, "RV102516"),
            null,
            Set.of(),
            new FotosDelProducto(Set.of(CREMA, MONASTERY), Map.of(), "{}", "ax franja"),
            T);

    crema.absorber(otro, PUBLICADAS);

    assertEquals(Optional.of(List.of("L", "XL")), crema.tallasPorTono().tallasDe("crema"));
    assertEquals(Optional.of(List.of("M")), crema.tallasPorTono().tallasDe("café"));
    assertEquals(Optional.of(List.of("M", "L")), crema.tallasPorTono().tallasDe("negro"));
    assertEquals(List.of("crema", "café", "negro"), crema.tonosNombrados());
  }

  /** Con tallas distintas y un lado sin tonos, no se sabe qué talla tiene cada color: no se une. */
  @Test
  void conTallasDistintasYSinTonosNoSeUne() {
    BorradorProducto crema = delAlbum("RV102517", CREMA, "crema", List.of("L"), "ax franja");
    BorradorProducto sinColor = delAlbum("RV102516", CAFE, "café", List.of("M"), "ax franja");
    sinColor.descartarFoto(CAFE);

    assertFalse(crema.tallasDeCadaTonoSeSaben(sinColor));
    assertThrows(ExcepcionDeDominio.class, () -> crema.absorber(sinColor, PUBLICADAS));
  }

  @Test
  void unaEtiquetaLargaSeRecortaALoQueCabe() {
    BorradorProducto largo = delAlbum("RV1", CREMA, "crema", List.of("L"), "x".repeat(300));

    assertEquals(BorradorProducto.LARGO_MAXIMO_DEL_DISENO, largo.diseno().orElseThrow().length());
  }
}
