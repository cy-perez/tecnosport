package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos.DisenoDeAlbum;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos.ProductoRepartido;
import co.tecnosport.api.domain.proveedores.RepartoDeFotos.Respaldo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Con las publicaciones del 9 de octubre de 2026 de La Riverah y Violeta. */
class RepartoDeFotosTest {

  private static final UUID F1 = UUID.randomUUID();
  private static final UUID F2 = UUID.randomUUID();
  private static final UUID F3 = UUID.randomUUID();
  private static final UUID F4 = UUID.randomUUID();

  private static ProductoExtraido producto(
      String titulo, TipoProductoProveedor tipo, String codigo) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.ROPA,
        tipo,
        Dinero.deCop(42000),
        Tallas.lista(List.of("S", "M", "L")),
        null,
        List.of(),
        null,
        "Una prenda.",
        null,
        false,
        new BigDecimal("0.9"),
        null,
        codigo);
  }

  private static LecturaDeFoto foto(int posicion, List<String> codigos, List<String> colores) {
    return new LecturaDeFoto(posicion, codigos, null, List.of(), colores, null);
  }

  private static LecturaDeFoto delAlbum(
      int posicion, String sku, List<String> tallas, String color, String diseno) {
    return new LecturaDeFoto(posicion, List.of(), sku, tallas, List.of(color), diseno);
  }

  @Test
  void elBodiYElJeanDeVioletaSeRepartenPorLaReferenciaImpresa() {
    // 15:08: las dos primeras fotos muestran el bodi y el jean («B:VY3010 J:Q355»); las otras dos,
    // el bodi en cada tono.
    ProductoExtraido bodi = producto("Bodi manga larga", TipoProductoProveedor.BODI, "VY3010");
    ProductoExtraido jean = producto("Jean baggy", TipoProductoProveedor.PANTALON, "Q355");
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            false,
            List.of(
                foto(0, List.of("B:VY3010", "J:Q355"), List.of("negro", "gris")),
                foto(1, List.of("B: VY3010", "J: Q355"), List.of("negro", "gris")),
                foto(2, List.of("VY3010"), List.of("negro")),
                foto(3, List.of("VY3010"), List.of("cocoa"))),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(bodi, jean), List.of(F1, F2, F3, F4), lectura);

    ProductoRepartido delBodi = repartidos.get(0);
    assertEquals(List.of(F1, F2, F3, F4), delBodi.fotos());
    assertEquals(Set.of(), delBodi.reparto().ajenas());
    assertEquals(Map.of(F3, "negro", F4, "cocoa"), delBodi.reparto().tonosSugeridos());
    assertEquals(Optional.of(F3), delBodi.exclusivaOpcional());
    assertEquals(Respaldo.TEXTO_IMPRESO, delBodi.respaldo());

    ProductoRepartido delJean = repartidos.get(1);
    assertEquals(List.of(F1, F2), delJean.fotos());
    assertEquals(Set.of(F3, F4), delJean.reparto().ajenas());
    assertEquals(Optional.empty(), delJean.exclusivaOpcional());
  }

  @Test
  void unaFotoSinReferenciaEsDeTodosYUnProductoSinFotoPropiaSeQuedaConTodas() {
    ProductoExtraido camiseta = producto("Camiseta slim", TipoProductoProveedor.CAMISETA, "261002");
    ProductoExtraido jogger = producto("Jogger", TipoProductoProveedor.SUDADERA, "VY3026");
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            false,
            List.of(foto(0, List.of(), List.of("cafe")), foto(1, List.of("J:VY3026"), List.of())),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(camiseta, jogger), List.of(F1, F2), lectura);

    assertEquals(List.of(F1, F2), repartidos.get(0).fotos());
    assertEquals(Respaldo.NINGUNO, repartidos.get(0).respaldo());
    assertEquals(List.of(F1, F2), repartidos.get(1).fotos());
    assertEquals(Respaldo.TEXTO_IMPRESO, repartidos.get(1).respaldo());
  }

  @Test
  void unCodigoQueElTextoNoTraeNoReparteNada() {
    ProductoExtraido falda = producto("Falda", TipoProductoProveedor.FALDA, null);
    ProductoExtraido basica = producto("Básica", TipoProductoProveedor.CAMISETA, null);
    LecturaDeFotos lectura =
        new LecturaDeFotos(false, List.of(foto(0, List.of("RV101282"), List.of("negro"))), "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(falda, basica), List.of(F1), lectura);

    repartidos.forEach(r -> assertEquals(Respaldo.NINGUNO, r.respaldo()));
    repartidos.forEach(r -> assertEquals(List.of(F1), r.fotos()));
  }

  /**
   * El álbum de La Riverah es un solo producto (decidido por el negocio el 13 de octubre de 2026):
   * con todas las fotos, sin código, y con las tallas del texto más las de los pies. Cada SKU es
   * una prenda, con sus fotos y las tallas de su pie.
   */
  @Test
  void elAlbumDeLaRiverahEsUnSoloProductoConUnaPrendaPorSku() {
    ProductoExtraido camisetas =
        producto("Camiseta oversize para caballero", TipoProductoProveedor.CAMISETA, null);
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                delAlbum(0, "RV102347", List.of("s", "M"), "negro", "swoosh rejilla"),
                delAlbum(1, "RV102336", List.of("S", "M", "L", "XL"), "gris", "jordan 23"),
                delAlbum(2, null, List.of(), "gris oscuro", "Jordan 23")),
            "{\"album\":true}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(camisetas), List.of(F1, F2, F3), lectura);

    assertEquals(1, repartidos.size());
    ProductoRepartido album = repartidos.getFirst();
    assertEquals(List.of(F1, F2, F3), album.fotos());
    assertEquals(Set.of(), album.reparto().ajenas());
    assertEquals(Optional.empty(), album.producto().codigoReferenciaOpcional());
    assertEquals(Tallas.lista(List.of("S", "M", "L", "XL")), album.producto().tallas());
    assertEquals(Optional.of(F1), album.exclusivaOpcional());
    assertEquals(Respaldo.TEXTO_IMPRESO, album.respaldo());
    assertEquals("{\"album\":true}", album.reparto().lecturaCruda());
    assertEquals(
        Map.of(F1, "negro", F2, "gris", F3, "gris oscuro"), album.reparto().tonosSugeridos());

    // La tercera no trae SKU y el lector dice que es el mismo diseño que la segunda: es la camiseta
    // del segundo SKU fotografiada sin el pie.
    assertEquals(
        List.of(
            new DisenoDeAlbum("RV102347", List.of("S", "M"), List.of(F1)),
            new DisenoDeAlbum("RV102336", List.of("S", "M", "L", "XL"), List.of(F2, F3))),
        RepartoDeFotos.disenosDelAlbum(List.of(F1, F2, F3), lectura));
  }

  /** Una foto que el lector no leyó no es de ningún diseño: vale para todas las prendas. */
  @Test
  void unaFotoSinLecturaEnUnAlbumNoEsDeNingunaPrenda() {
    ProductoExtraido camisetas = producto("Camiseta", TipoProductoProveedor.CAMISETA, null);
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                delAlbum(0, "RV1", List.of("S"), "azul", "boss"),
                delAlbum(2, "RV2", List.of("M"), "negro", "msm")),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(camisetas), List.of(F1, F2, F3), lectura);

    assertEquals(1, repartidos.size());
    assertEquals(List.of(F1, F2, F3), repartidos.getFirst().fotos());
    assertEquals(
        List.of(List.of(F1), List.of(F3)),
        RepartoDeFotos.disenosDelAlbum(List.of(F1, F2, F3), lectura).stream()
            .map(DisenoDeAlbum::fotos)
            .toList());
  }

  @Test
  void unSoloDisenoMasUnaFotoIlegibleNoEsUnAlbum() {
    ProductoExtraido camiseta = producto("Camiseta", TipoProductoProveedor.CAMISETA, null);
    LecturaDeFotos lectura =
        new LecturaDeFotos(true, List.of(delAlbum(0, null, List.of(), "azul", "boss")), "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(camiseta), List.of(F1, F2), lectura);

    assertEquals(1, repartidos.size());
    assertEquals(List.of(F1, F2), repartidos.getFirst().fotos());
    assertTrue(RepartoDeFotos.disenosDelAlbum(List.of(F1, F2), lectura).isEmpty());
  }

  /**
   * El producto del álbum no lleva el código del texto: es el de uno de los diseños, o de ninguno.
   * Dos fotos con el mismo SKU son la misma prenda, aunque el lector les ponga diseños distintos.
   */
  @Test
  void elAlbumNoHeredaElCodigoDelTextoYElMismoSkuEsLaMismaPrenda() {
    ProductoExtraido jeans = producto("Jean importado", TipoProductoProveedor.PANTALON, "REF123");
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                delAlbum(0, null, List.of(), "azul", "rotos"),
                delAlbum(1, "RV9", List.of(), "negro", "parches"),
                delAlbum(2, "RV9", List.of(), "gris", "pintura"),
                delAlbum(3, "RV7", List.of(), "azul", "lisos")),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(jeans), List.of(F1, F2, F3, F4), lectura);

    assertEquals(1, repartidos.size());
    assertEquals(Optional.empty(), repartidos.getFirst().producto().codigoReferenciaOpcional());
    assertEquals(
        List.of(
            new DisenoDeAlbum("RV9", List.of(), List.of(F2, F3)),
            new DisenoDeAlbum("RV7", List.of(), List.of(F4))),
        RepartoDeFotos.disenosDelAlbum(List.of(F1, F2, F3, F4), lectura));
  }

  /**
   * Los jeans de las 19:19 del 9 de octubre de 2026: en tres corridas contra la verdad, el lector
   * juntó de maneras distintas diez jeans con diez SKU. Por SKU da igual cómo los agrupe: son diez
   * prendas de un solo producto.
   */
  @Test
  void cadaSkuEsUnaPrendaAunqueElLectorLosJunte() {
    UUID f5 = UUID.randomUUID();
    ProductoExtraido jeans = producto("Jean licrado", TipoProductoProveedor.PANTALON, null);
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                delAlbum(0, "RV491", List.of("30"), "azul", "jean clasico"),
                delAlbum(1, "RV494", List.of("32"), "azul oscuro", "jean clasico"),
                delAlbum(2, "RV489", List.of("34"), "negro", "jean negro"),
                delAlbum(3, "RV490", List.of("36"), "azul", "jean clasico"),
                delAlbum(4, "RV485", List.of("30"), "azul", "jean clasico")),
            "{}");

    List<DisenoDeAlbum> disenos =
        RepartoDeFotos.disenosDelAlbum(List.of(F1, F2, F3, F4, f5), lectura);

    assertEquals(
        List.of(List.of(F1), List.of(F2), List.of(F3), List.of(F4), List.of(f5)),
        disenos.stream().map(DisenoDeAlbum::fotos).toList());
    assertEquals(List.of("32"), disenos.get(1).tallas());
    assertEquals(
        1, RepartoDeFotos.repartir(List.of(jeans), List.of(F1, F2, F3, F4, f5), lectura).size());
  }

  /**
   * El lector dijo que no era un álbum, pero el pie trae un SKU por foto: los diez jeans de las
   * 19:19 en la segunda corrida contra la verdad. Y la falda con un SKU por color es una falda en
   * dos prendas.
   */
  @Test
  void dosSkuDistintosImpresosHacenAlbumAunqueElLectorDigaQueNo() {
    LecturaDeFotos noEsAlbumDiceElLector =
        new LecturaDeFotos(
            false,
            List.of(
                delAlbum(0, "RV491", List.of(), "azul", "jean"),
                delAlbum(1, "RV494", List.of(), "azul", "jean"),
                delAlbum(2, "RV489", List.of(), "negro", "jean")),
            "{}");
    LecturaDeFotos falda =
        new LecturaDeFotos(
            false,
            List.of(
                delAlbum(0, "RV101282", List.of(), "negro", "falda tableada"),
                delAlbum(1, "RV101281", List.of(), "gris", "falda tableada")),
            "{}");

    assertEquals(
        3, RepartoDeFotos.disenosDelAlbum(List.of(F1, F2, F3), noEsAlbumDiceElLector).size());
    assertEquals(2, RepartoDeFotos.disenosDelAlbum(List.of(F1, F2), falda).size());
    assertEquals(
        1,
        RepartoDeFotos.repartir(
                List.of(producto("Falda", TipoProductoProveedor.FALDA, null)),
                List.of(F1, F2),
                falda)
            .size());
  }

  /** Lo que el lector juntó con la talla en el pie no es una talla: «XLNEGRO». */
  @Test
  void loQueNoPareceUnaTallaNoEntraEnLasDelPie() {
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                delAlbum(0, "RV102346", List.of("S", "M", "L", "XLNEGRO"), "blanco", "msm"),
                delAlbum(1, "RV102348", List.of("S", "M", "L"), "gris", "jordan")),
            "{}");

    assertEquals(
        List.of("S", "M", "L"),
        RepartoDeFotos.disenosDelAlbum(List.of(F1, F2), lectura).getFirst().tallas());
  }

  /** Sin SKU no hay con qué saber que son dos: el mismo color puede ser frente y espalda. */
  @Test
  void sinSkuNiElDisenoNiElColorSeparan() {
    ProductoExtraido camisetas = producto("Camiseta", TipoProductoProveedor.CAMISETA, null);
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                delAlbum(0, null, List.of(), "negro", "msm"),
                delAlbum(1, null, List.of(), "negro", "msm"),
                delAlbum(2, null, List.of(), "azul", "boss")),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(camisetas), List.of(F1, F2, F3), lectura);

    assertEquals(1, repartidos.size());
    assertEquals(List.of(F1, F2, F3), repartidos.getFirst().fotos());
  }

  @Test
  void delPieConDosBloquesValeElDeLaFechaMasRecienteYSinFechasElUltimo() {
    LecturaDeFoto conFechas =
        LecturaDeFoto.conPie(
            0,
            List.of(),
            List.of(
                new LecturaDeFoto.BloqueDePie(
                    "RV101862", java.time.LocalDate.of(2026, 10, 2), List.of("S", "M", "L", "XL")),
                new LecturaDeFoto.BloqueDePie(
                    "RV102347", java.time.LocalDate.of(2026, 10, 9), List.of("S", "M"))),
            List.of("negro"),
            "swoosh");
    LecturaDeFoto alReves =
        LecturaDeFoto.conPie(
            0,
            List.of(),
            List.of(
                new LecturaDeFoto.BloqueDePie(
                    "RV102347", java.time.LocalDate.of(2026, 10, 9), List.of("S", "M")),
                new LecturaDeFoto.BloqueDePie(
                    "RV101862", java.time.LocalDate.of(2026, 10, 2), List.of("S"))),
            List.of(),
            null);
    LecturaDeFoto sinFechas =
        LecturaDeFoto.conPie(
            0,
            List.of(),
            List.of(
                new LecturaDeFoto.BloqueDePie("RV1", null, List.of("S")),
                new LecturaDeFoto.BloqueDePie("RV2", null, List.of("M"))),
            List.of(),
            null);

    assertEquals("RV102347", conFechas.sku());
    assertEquals(List.of("S", "M"), conFechas.tallasDelPie());
    assertEquals("RV102347", alReves.sku());
    assertEquals("RV2", sinFechas.sku());
    assertEquals(null, LecturaDeFoto.conPie(0, List.of(), List.of(), List.of(), null).sku());
  }

  /**
   * Imperio Wicho, 11 de octubre de 2026: el mismo tenis en negro, negro con la franja blanca y
   * blanco con la franja azul y roja. El lector dijo álbum y les puso etiquetas distintas aunque
   * sus instrucciones describen este mismo tenis como un solo producto; salieron tres borradores.
   * Sin SKU, uno, con el tono de la foto que muestra uno solo.
   */
  @Test
  void sinSkuElAlbumQueVeElLectorEsUnSoloProducto() {
    ProductoExtraido tenis = producto("Tenis para caballero", TipoProductoProveedor.TENIS, null);
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                delAlbum(0, null, List.of(), "negro", "tenis liso"),
                new LecturaDeFoto(
                    1, List.of(), null, List.of(), List.of("negro", "blanco"), "tenis franja"),
                new LecturaDeFoto(
                    2,
                    List.of(),
                    null,
                    List.of(),
                    List.of("blanco", "azul", "rojo"),
                    "tenis franja cruzada")),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(tenis), List.of(F1, F2, F3), lectura);

    assertEquals(1, repartidos.size());
    assertEquals(List.of(F1, F2, F3), repartidos.getFirst().fotos());
    assertEquals(Map.of(F1, "negro"), repartidos.getFirst().reparto().tonosSugeridos());
  }

  @Test
  void unProductoConVariasVistasNoEsUnAlbumAunqueElLectorLoDiga() {
    ProductoExtraido blusa = producto("Blusa licrada", TipoProductoProveedor.BLUSA, "VY2945");
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                new LecturaDeFoto(
                    0,
                    List.of("VY2945"),
                    null,
                    List.of(),
                    List.of("negro", "cocoa", "verde"),
                    "blusa"),
                new LecturaDeFoto(
                    1,
                    List.of("VY2945"),
                    null,
                    List.of(),
                    List.of("negro", "cocoa", "verde"),
                    "blusa")),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(blusa), List.of(F1, F2), lectura);

    assertEquals(1, repartidos.size());
    assertEquals(List.of(F1, F2), repartidos.getFirst().fotos());
    // La consolidada muestra los tres tonos: no sugiere ninguno.
    assertEquals(Map.of(), repartidos.getFirst().reparto().tonosSugeridos());
  }

  private static ProductoExtraido conTonos(String titulo, Integer cantidadTonos) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.BOLSOS,
        TipoProductoProveedor.CANGURO,
        Dinero.deCop(45000),
        Tallas.desconocida(),
        cantidadTonos,
        List.of(),
        "poliuretano",
        "Un bolso.",
        null,
        false,
        new BigDecimal("0.9"),
        null,
        null);
  }

  /** D'Osman, 11 de octubre de 2026: el mismo bolso en tres estampados, sin SKU. */
  private static LecturaDeFotos manosLibresEnTresEstampados() {
    return new LecturaDeFotos(
        true,
        List.of(
            new LecturaDeFoto(
                0,
                List.of(),
                null,
                List.of(),
                List.of("cafe", "beige", "crema"),
                "monograma rombos"),
            delAlbum(1, null, List.of(), "cafe", "monograma rombos"),
            delAlbum(2, null, List.of(), "beige", "monograma letras"),
            delAlbum(3, null, List.of(), "crema", "monograma letras")),
        "{}");
  }

  @Test
  void losEstampadosQueCabenEnLosTonosDelTextoSonUnSoloProducto() {
    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(
            List.of(conTonos("Manos libres de dama", 3)),
            List.of(F1, F2, F3, F4),
            manosLibresEnTresEstampados());

    assertEquals(1, repartidos.size());
    assertEquals(List.of(F1, F2, F3, F4), repartidos.getFirst().fotos());
    assertEquals(Set.of(), repartidos.getFirst().reparto().ajenas());
  }

  @Test
  void sinTonosEnElTextoTampocoSeParte() {
    assertEquals(
        1,
        RepartoDeFotos.repartir(
                List.of(conTonos("Manos libres de dama", null)),
                List.of(F1, F2, F3, F4),
                manosLibresEnTresEstampados())
            .size());
  }

  @Test
  void soloUnSkuPorFotoHaceUnAlbumAunqueHayaMasDisenosQueTonos() {
    LecturaDeFotos tresDisenos =
        new LecturaDeFotos(
            true,
            List.of(
                delAlbum(0, null, List.of(), "negro", "msm"),
                delAlbum(1, null, List.of(), "azul", "boss"),
                delAlbum(2, null, List.of(), "gris", "jordan 23")),
            "{}");
    LecturaDeFotos conSku =
        new LecturaDeFotos(
            true,
            List.of(
                delAlbum(0, "RV1", List.of(), "negro", "msm"),
                delAlbum(1, "RV2", List.of(), "azul", "boss")),
            "{}");

    assertEquals(
        1,
        RepartoDeFotos.repartir(List.of(conTonos("Bolso", 2)), List.of(F1, F2, F3), tresDisenos)
            .size());
    assertTrue(RepartoDeFotos.disenosDelAlbum(List.of(F1, F2, F3), tresDisenos).isEmpty());
    assertEquals(2, RepartoDeFotos.disenosDelAlbum(List.of(F1, F2), conSku).size());
  }

  @Test
  void sinLecturaEsElRepartoDeAntes() {
    ProductoExtraido bolso = producto("Bolso", TipoProductoProveedor.BOLSO, null);

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(bolso), List.of(F1, F2), null);

    assertEquals(List.of(F1, F2), repartidos.getFirst().fotos());
    assertEquals(Respaldo.NINGUNO, repartidos.getFirst().respaldo());
    assertEquals(null, repartidos.getFirst().reparto().lecturaCruda());
  }

  @Test
  void elPrefijoDeTipoDeVioletaNoEsParteDelCodigo() {
    LecturaDeFoto leida = foto(0, List.of("C:261002", "j: vy3026", "(Q326)"), List.of());

    assertEquals(List.of("261002", "VY3026", "Q326"), leida.codigos());
    assertTrue(leida.muestra("vy3026"));
  }
}
