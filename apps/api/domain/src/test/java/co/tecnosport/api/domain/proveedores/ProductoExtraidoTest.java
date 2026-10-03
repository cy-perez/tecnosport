package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
