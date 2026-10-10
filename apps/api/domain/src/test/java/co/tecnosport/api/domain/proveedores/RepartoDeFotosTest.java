package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
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

  @Test
  void elAlbumDeLaRiverahSeParteEnUnProductoPorDisenoConLasTallasYElSkuDelPie() {
    ProductoExtraido camisetas =
        producto("Camiseta oversize para caballero", TipoProductoProveedor.CAMISETA, null);
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                delAlbum(0, "RV102347", List.of("s", "M"), "negro", "swoosh rejilla"),
                delAlbum(1, "RV102336", List.of("S", "M", "L"), "gris", "jordan 23"),
                delAlbum(2, null, List.of(), "gris oscuro", "Jordan 23")),
            "{\"album\":true}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(camisetas), List.of(F1, F2, F3), lectura);

    assertEquals(2, repartidos.size());
    ProductoRepartido primero = repartidos.get(0);
    assertEquals(List.of(F1), primero.fotos());
    assertEquals(Set.of(F2, F3), primero.reparto().ajenas());
    assertEquals(Optional.of("RV102347"), primero.producto().codigoReferenciaOpcional());
    assertEquals(Tallas.lista(List.of("S", "M")), primero.producto().tallas());
    assertEquals(Optional.of(F1), primero.exclusivaOpcional());
    assertEquals("{\"album\":true}", primero.reparto().lecturaCruda());

    // El mismo diseño en dos colores: un producto, y sin SKU porque no todas sus fotos lo traen.
    ProductoRepartido segundo = repartidos.get(1);
    assertEquals(List.of(F2, F3), segundo.fotos());
    assertEquals(Optional.empty(), segundo.producto().codigoReferenciaOpcional());
    assertEquals(Map.of(F2, "gris", F3, "gris oscuro"), segundo.reparto().tonosSugeridos());
    assertEquals(Respaldo.TEXTO_IMPRESO, segundo.respaldo());
  }

  /**
   * Una foto que el lector no leyó —omitida, ilegible o que el modelo no devolvió— no es un diseño:
   * es de todos. Antes era un diseño ella sola, un borrador basura que además se la quitaba al
   * real.
   */
  @Test
  void unaFotoSinLecturaEnUnAlbumEsDeTodosLosDisenosYNoUnDisenoMas() {
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

    assertEquals(2, repartidos.size());
    assertEquals(List.of(F1, F2), repartidos.get(0).fotos());
    assertEquals(List.of(F2, F3), repartidos.get(1).fotos());
    assertEquals(Optional.of(F3), repartidos.get(1).exclusivaOpcional());
    repartidos.forEach(r -> assertTrue(r.disenoDeAlbum()));
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
  }

  /**
   * Con el código del texto, o con el mismo SKU, los diseños 2..N tendrían la misma huella que el
   * primero y se descartarían por «la misma referencia», con sus fotos perdidas en el primero.
   */
  @Test
  void unDisenoNoHeredaElCodigoDelTextoYUnSkuRepetidoNoIdentificaANinguno() {
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

    assertEquals(
        List.of(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("RV7")),
        repartidos.stream().map(r -> r.producto().codigoReferenciaOpcional()).toList());
  }

  @Test
  void unDisenoSinPieSeRepartePeroSinRespaldo() {
    ProductoExtraido camisetas = producto("Camiseta", TipoProductoProveedor.CAMISETA, null);
    LecturaDeFotos lectura =
        new LecturaDeFotos(
            true,
            List.of(
                delAlbum(0, null, List.of(), "azul", "boss"),
                delAlbum(1, null, List.of(), "negro", "msm")),
            "{}");

    List<ProductoRepartido> repartidos =
        RepartoDeFotos.repartir(List.of(camisetas), List.of(F1, F2), lectura);

    assertEquals(2, repartidos.size());
    repartidos.forEach(r -> assertEquals(Respaldo.DISENO_SIN_PIE, r.respaldo()));
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
