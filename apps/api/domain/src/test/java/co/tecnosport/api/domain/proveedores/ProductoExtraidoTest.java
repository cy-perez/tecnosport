package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductoExtraidoTest {

  private static ProductoExtraido bolso(BigDecimal confianza) {
    return new ProductoExtraido(
        true,
        false,
        " Bolso de dama mediano ",
        LineaCatalogo.BOLSOS,
        TipoProductoProveedor.BOLSO,
        Dinero.deCop(53000),
        Tallas.desconocida(),
        4,
        null,
        "importado",
        "incluye llavero.",
        null,
        false,
        confianza,
        "  ");
  }

  @Test
  void loQueElMensajeNoDiceQuedaVacioYNadaSeRellena() {
    ProductoExtraido extraido = bolso(new BigDecimal("0.92"));

    assertEquals(Optional.of("Bolso de dama mediano"), extraido.tituloOpcional());
    assertEquals(List.of(), extraido.tonosNombrados());
    assertEquals(null, extraido.notas());
    assertEquals(Optional.of(LineaCatalogo.BOLSOS), extraido.lineaOpcional());
    assertEquals(Optional.of(Dinero.deCop(53000)), extraido.precioProveedorOpcional());
  }

  @Test
  void sinTipoNiTallasCaeEnOtroYDesconocida() {
    ProductoExtraido extraido =
        new ProductoExtraido(
            true,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            BigDecimal.ONE,
            null);

    assertEquals(TipoProductoProveedor.OTRO, extraido.tipo());
    assertEquals(TipoDeTalla.DESCONOCIDA, extraido.tallas().tipo());
    assertEquals(Optional.empty(), extraido.tituloOpcional());
    assertEquals(Optional.empty(), extraido.lineaOpcional());
  }

  @Test
  void laConfianzaVaDeCeroAUno() {
    assertThrows(ExcepcionDeDominio.class, () -> bolso(new BigDecimal("1.01")));
    assertThrows(ExcepcionDeDominio.class, () -> bolso(new BigDecimal("-0.1")));
    assertThrows(ExcepcionDeDominio.class, () -> bolso(null));
  }

  @Test
  void lasTallasSeSostienenSolas() {
    assertEquals(Optional.of("L"), Tallas.unica("L").sirveHastaOpcional());
    assertEquals(List.of("M", "L"), Tallas.lista(List.of("M", "L")).valores());
    assertThrows(ExcepcionDeDominio.class, () -> Tallas.lista(List.of()));
    assertThrows(ExcepcionDeDominio.class, () -> new Tallas(TipoDeTalla.UNICA, "L", List.of("M")));
    assertEquals(TipoDeTalla.DESCONOCIDA, new Tallas(null, null, null).tipo());
  }

  private static ProductoExtraido bodi(Tallas tallas, boolean esReplica) {
    return new ProductoExtraido(
        true,
        false,
        "Body  Herraje",
        LineaCatalogo.ROPA,
        TipoProductoProveedor.BODI,
        Dinero.deCop(30000),
        tallas,
        null,
        null,
        null,
        "Bodi con herraje metálico.",
        "Hardware bodysuit",
        esReplica,
        BigDecimal.ONE,
        null);
  }

  @Test
  void elTituloSaleCorregido() {
    assertEquals(Optional.of("Bodi Herraje"), bodi(Tallas.desconocida(), false).tituloOpcional());
  }

  /** Talla única sin «sirve hasta» en el texto: el límite que dijo el extractor se cae. */
  @Test
  void elSirveHastaQueElTextoNoEscribeSeCae() {
    ProductoExtraido contrastado =
        bodi(Tallas.unica("L"), false).contrastadoCon("*Body Herraje* talla única 💲30");

    assertEquals(TipoDeTalla.UNICA, contrastado.tallas().tipo());
    assertEquals(Optional.empty(), contrastado.tallas().sirveHastaOpcional());
  }

  /** WhatsApp a veces pega las palabras con un espacio duro. */
  @Test
  void elSirveHastaConEspacioDuroTambienCuenta() {
    ProductoExtraido contrastado =
        bodi(Tallas.unica("L"), false).contrastadoCon("Talla única, sirve hasta la L");

    assertEquals(Optional.of("L"), contrastado.tallas().sirveHastaOpcional());
  }

  @Test
  void elSirveHastaQueElTextoEscribeSeQueda() {
    ProductoExtraido contrastado =
        bodi(Tallas.unica("L"), false).contrastadoCon("Talla única, SIRVE HASTA la L 💲30");

    assertEquals(Optional.of("L"), contrastado.tallas().sirveHastaOpcional());
  }

  @Test
  void elUnoPuntoUnoDelTextoLoVuelveReplicaAunqueElExtractorNoLoDiga() {
    assertEquals(
        true,
        bodi(Tallas.desconocida(), false).contrastadoCon("*NUEVA COLECCIÓN 1.1*").esReplica());
    assertEquals(false, bodi(Tallas.desconocida(), false).contrastadoCon("Bodi 💲30").esReplica());
    assertEquals(true, bodi(Tallas.desconocida(), true).contrastadoCon("Bodi 💲30").esReplica());
  }

  /** La AAA cuenta como la 1.1 desde el 4 de octubre de 2026; las pilas AAA, no. */
  @Test
  void laTripleADelTextoLoVuelveReplica() {
    assertEquals(
        true, bodi(Tallas.desconocida(), false).contrastadoCon("Adidas Importado AAA").esReplica());
    assertEquals(
        false,
        bodi(Tallas.desconocida(), false).contrastadoCon("Control con pilas AAA").esReplica());
  }

  private static ProductoExtraido conTipo(TipoProductoProveedor tipo, Tallas tallas) {
    return new ProductoExtraido(
        true,
        false,
        "Jean clásico",
        LineaCatalogo.ROPA,
        tipo,
        Dinero.deCop(85000),
        tallas,
        null,
        null,
        null,
        null,
        null,
        false,
        BigDecimal.ONE,
        null);
  }

  /** Aunque el extractor solo devuelva los extremos: el rango de un pantalón va de 2 en 2. */
  @Test
  void elRangoDeUnPantalonOUnShortVaDeDosEnDos() {
    Tallas extremos = Tallas.lista(List.of("30", "36"));
    String texto = "Jean clásico 💲85 · Tallas 30 a la 36";

    assertEquals(
        List.of("30", "32", "34", "36"),
        conTipo(TipoProductoProveedor.PANTALON, extremos).contrastadoCon(texto).tallas().valores());
    assertEquals(
        List.of("30", "32", "34", "36"),
        conTipo(TipoProductoProveedor.SHORT, Tallas.desconocida())
            .contrastadoCon(texto)
            .tallas()
            .valores());
  }

  /** En calzado «34 al 40» es de 1 en 1: lo que dijo el extractor se queda. */
  @Test
  void elRangoDeOtroTipoNoSeToca() {
    Tallas deUnoEnUno = Tallas.lista(List.of("34", "35", "36", "37", "38", "39", "40"));

    assertEquals(
        deUnoEnUno,
        conTipo(TipoProductoProveedor.TENIS, deUnoEnUno)
            .contrastadoCon("Tenis tallas 34 al 40")
            .tallas());
  }

  private static ProductoExtraido conCodigo(String codigo) {
    return new ProductoExtraido(
        true,
        false,
        "Jean costuras contrastadas",
        LineaCatalogo.ROPA,
        TipoProductoProveedor.PANTALON,
        Dinero.deCop(124000),
        Tallas.desconocida(),
        null,
        List.of(),
        null,
        null,
        null,
        false,
        new BigDecimal("0.9"),
        null,
        codigo);
  }

  /** El código se guarda como el proveedor lo marca, sin paréntesis ni espacios. */
  @Test
  void elCodigoDeReferenciaSeNormaliza() {
    assertEquals(Optional.of("VY2777"), conCodigo("( vy2777)").codigoReferenciaOpcional());
    assertEquals(Optional.of("261003"), conCodigo("261003").codigoReferenciaOpcional());
    assertEquals(Optional.of("M4"), conCodigo("M4").codigoReferenciaOpcional());
  }

  /** Sin una cifra, o demasiado largo, no es un código: es una palabra o una frase. */
  @Test
  void loQueNoPareceUnCodigoNoLoEs() {
    assertEquals(Optional.empty(), conCodigo("DYNAMIC").codigoReferenciaOpcional());
    assertEquals(Optional.empty(), conCodigo("Q1234567890123").codigoReferenciaOpcional());
    assertEquals(Optional.empty(), conCodigo("  ").codigoReferenciaOpcional());
  }

  /**
   * El código identifica el producto, así que uno que el texto no escribe —inventado, o de otro
   * producto del mensaje mal leído— no se cree.
   */
  @Test
  void elCodigoSoloValeSiElTextoLoEscribe() {
    String texto = "Jean costuras contrastadas ( Q339)\n💲124\nTalla S M L";

    assertEquals(
        Optional.of("Q339"), conCodigo("Q339").contrastadoCon(texto).codigoReferenciaOpcional());
    assertEquals(
        Optional.empty(), conCodigo("Q340").contrastadoCon(texto).codigoReferenciaOpcional());
  }

  private static ProductoExtraido pantalon(String titulo, boolean esReplica) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.ROPA,
        TipoProductoProveedor.PANTALON,
        Dinero.deCop(75000),
        Tallas.desconocida(),
        null,
        List.of(),
        null,
        null,
        null,
        esReplica,
        new BigDecimal("0.9"),
        null);
  }

  /** El «Calidad 1.1» sin marca del pantalón de La Riverah: el título termina en «importado». */
  @Test
  void laReplicaSinMarcaDelTextoTerminaEnImportado() {
    String texto = "*Pantalón Jogger para dama*\n✨Calidad 1.1\n❤️Bota recta\n🤑75.000🥳";

    ProductoExtraido contrastado =
        pantalon("Pantalón jogger para dama estilo", false).contrastadoCon(texto);

    assertEquals(Optional.of("Pantalón jogger para dama importado"), contrastado.tituloOpcional());
    assertTrue(contrastado.esReplica());
  }

  /** Sin «1.1» ni «AAA» no es réplica, y el título no se toca. */
  @Test
  void loQueNoEsReplicaNoSeTitulaImportado() {
    ProductoExtraido contrastado =
        pantalon("Pantalón jogger para dama", false)
            .contrastadoCon("*Pantalón Jogger para dama*\n🤑75.000🥳");

    assertEquals(Optional.of("Pantalón jogger para dama"), contrastado.tituloOpcional());
  }
}
