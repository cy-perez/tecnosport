package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El anuncio nombra las tallas corridas y el borrador nace con las que se venden (ADR-0085). */
class TallasCorridasDelBorradorTest {

  private static final Instant T = Instant.parse("2026-10-13T15:00:00Z");

  private static BorradorProducto borrador(
      TipoProductoProveedor tipo, Tallas tallas, TallasPorTono porTono) {
    ProductoExtraido extraido =
        new ProductoExtraido(
            true,
            false,
            "Polo estilo Puma - Ferrari",
            LineaCatalogo.ROPA,
            tipo,
            Dinero.deCop(50000),
            tallas,
            null,
            List.of(),
            null,
            "Polo de tela fría.",
            null,
            true,
            new BigDecimal("0.9"),
            null,
            null,
            porTono,
            List.of());
    return BorradorProducto.nuevo(
        UUID.randomUUID(),
        UUID.randomUUID(),
        extraido,
        "{}",
        Dinero.deCop(50000),
        Dinero.deCop(69900),
        null,
        null,
        Set.of(),
        T);
  }

  private static BorradorProducto borrador(TipoProductoProveedor tipo, Tallas tallas) {
    return borrador(tipo, tallas, TallasPorTono.ninguna());
  }

  /** Meraki: «Ojo Horma reducida pedir una talla más · Tallas M-L-XL-XXL». */
  @Test
  void laHormaReducidaSeVendeUnaTallaAbajo() {
    BorradorProducto polo =
        borrador(TipoProductoProveedor.POLO, Tallas.lista(List.of("M", "L", "XL", "XXL")));

    BorradorProducto.conTallasCorridas(polo, new AjusteDeTalla(1));

    assertEquals(Tallas.lista(List.of("S", "M", "L", "XL")), polo.tallas());
    assertEquals(new AjusteDeTalla(1), polo.ajusteDeTalla());
    assertTrue(polo.alertas().contains(AlertaBorrador.TALLAS_CORRIDAS));
  }

  /** Imperio Wicho: «Tallas disponibles 40 a la 45 · Aumentar 2 tallas». */
  @Test
  void enCalzadoSeRestanLosNumeros() {
    BorradorProducto tenis =
        borrador(
            TipoProductoProveedor.TENIS, Tallas.lista(List.of("40", "41", "42", "43", "44", "45")));

    BorradorProducto.conTallasCorridas(tenis, new AjusteDeTalla(2));

    assertEquals(Tallas.lista(List.of("38", "39", "40", "41", "42", "43")), tenis.tallas());
  }

  @Test
  void lasDeCadaTonoTambienSeCorren() {
    BorradorProducto polo =
        borrador(
            TipoProductoProveedor.POLO,
            Tallas.lista(List.of("M", "L")),
            new TallasPorTono(
                List.of(
                    new TallasPorTono.TallasDeUnTono("negro", List.of("M", "L")),
                    new TallasPorTono.TallasDeUnTono("blanco", List.of("L")))));

    BorradorProducto.conTallasCorridas(polo, new AjusteDeTalla(1));

    assertEquals(Optional.of(List.of("S", "M")), polo.tallasPorTono().tallasDe("negro"));
    assertEquals(Optional.of(List.of("M")), polo.tallasPorTono().tallasDe("blanco"));
  }

  /** Todas o ninguna: la XS una más abajo no existe, y se quedan las del anuncio para revisar. */
  @Test
  void siUnaNoSePuedeCorrerSeQuedanLasDelAnuncio() {
    BorradorProducto polo =
        borrador(TipoProductoProveedor.POLO, Tallas.lista(List.of("XS", "S", "M")));

    BorradorProducto.conTallasCorridas(polo, new AjusteDeTalla(1));

    assertEquals(Tallas.lista(List.of("XS", "S", "M")), polo.tallas());
    assertEquals(new AjusteDeTalla(1), polo.ajusteDeTalla(), "el ajuste sí queda, para corregir");
    assertTrue(polo.alertas().contains(AlertaBorrador.TALLAS_SIN_CORRER));
    assertFalse(polo.alertas().contains(AlertaBorrador.TALLAS_CORRIDAS));
  }

  /** Una talla única se pide como se vende: no queda ajuste que deje la aprobación bloqueada. */
  @Test
  void unaTallaUnicaNoSeCorreNiGuardaAjuste() {
    BorradorProducto polo = borrador(TipoProductoProveedor.POLO, Tallas.unica(null));

    BorradorProducto.conTallasCorridas(polo, new AjusteDeTalla(1));

    assertEquals(Tallas.unica(null), polo.tallas());
    assertTrue(polo.ajusteDeTalla().ninguno());
    assertTrue(polo.alertas().isEmpty());
  }

  /** Sin tallas leídas el ajuste queda, y la alerta pide escribir las que se venden. */
  @Test
  void sinTallasElAjusteQuedaConSuAlerta() {
    BorradorProducto tenis = borrador(TipoProductoProveedor.TENIS, Tallas.desconocida());

    BorradorProducto.conTallasCorridas(tenis, new AjusteDeTalla(1));

    assertEquals(new AjusteDeTalla(1), tenis.ajusteDeTalla());
    assertTrue(tenis.alertas().contains(AlertaBorrador.TALLAS_SIN_CORRER));
  }

  @Test
  void losDosGenerosDelMismoAnuncioSonGemelos() {
    BorradorProducto dama =
        BorradorProducto.nuevo(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ProductoExtraido(
                true,
                false,
                "Tenis estilo Nike unisex",
                LineaCatalogo.CALZADO,
                TipoProductoProveedor.TENIS,
                Dinero.deCop(85000),
                Tallas.lista(List.of("41")),
                null,
                List.of(),
                null,
                "Tenis.",
                null,
                false,
                new BigDecimal("0.9"),
                null,
                null),
            "{}",
            Dinero.deCop(85000),
            Dinero.deCop(110000),
            HuellaProveedor.calcular(UUID.randomUUID(), "Tenis", Dinero.deCop(85000)),
            null,
            Set.of(),
            T);
    BorradorProducto caballero =
        dama.delOtroGenero("Tenis para caballero", new AjusteDeTalla(1), T);
    BorradorProducto otro = borrador(TipoProductoProveedor.TENIS, Tallas.lista(List.of("41")));

    assertTrue(dama.esGemeloDe(caballero));
    assertTrue(caballero.esGemeloDe(dama));
    assertFalse(dama.esGemeloDe(otro));
    assertEquals(
        Optional.of(HuellaProveedor.delGemelo(dama.huella().orElseThrow())), caballero.huella());
  }

  /** Lo que el panel enseña al lado de cada talla, con el paso del tipo de prenda. */
  @Test
  void cadaTallaDiceComoSeLePideAlProveedor() {
    BorradorProducto polo =
        borrador(TipoProductoProveedor.POLO, Tallas.lista(List.of("S", "XXXL")));
    BorradorProducto.conAjusteDeTalla(polo, new AjusteDeTalla(1));

    assertEquals(
        List.of(
            new BorradorProducto.TallaPedida("S", "M"),
            new BorradorProducto.TallaPedida("XXXL", null)),
        polo.tallasPedidas());

    BorradorProducto jean = borrador(TipoProductoProveedor.PANTALON, Tallas.lista(List.of("30")));
    BorradorProducto.conAjusteDeTalla(jean, new AjusteDeTalla(1));
    assertEquals(List.of(new BorradorProducto.TallaPedida("30", "32")), jean.tallasPedidas());
  }

  @Test
  void sinAjusteNoHayTallasPedidas() {
    BorradorProducto polo = borrador(TipoProductoProveedor.POLO, Tallas.lista(List.of("S")));

    assertTrue(polo.tallasPedidas().isEmpty());
  }

  @Test
  void elAjusteSoloSeCambiaEnRevision() {
    BorradorProducto polo = borrador(TipoProductoProveedor.POLO, Tallas.lista(List.of("S")));
    polo.cambiarAjusteDeTalla(new AjusteDeTalla(1));
    assertEquals(new AjusteDeTalla(1), polo.ajusteDeTalla());
    assertEquals(
        Tallas.lista(List.of("S")), polo.tallas(), "cambiar el ajuste no corre las tallas");

    polo.rechazar("No es de la tienda.");
    org.junit.jupiter.api.Assertions.assertThrows(
        co.tecnosport.api.domain.compartido.ExcepcionDeDominio.class,
        () -> polo.cambiarAjusteDeTalla(AjusteDeTalla.NINGUNO));
  }

  /**
   * IMG-20261010-WA0150 de Imperio Wicho: para dama 35, 36, 38, 41 y 42; para caballero una talla
   * abajo, 34, 35, 37, 40 y 41. Las mismas fotos, otro título y otra huella.
   */
  @Test
  void elBorradorDeCaballeroCorreLasTallasConSuAjuste() {
    BorradorProducto dama =
        borrador(TipoProductoProveedor.TENIS, Tallas.lista(List.of("35", "36", "38", "41", "42")));

    BorradorProducto caballero =
        dama.delOtroGenero("Tenis estilo Nike para caballero", new AjusteDeTalla(1), T);

    assertEquals(Tallas.lista(List.of("34", "35", "37", "40", "41")), caballero.tallas());
    assertEquals(new AjusteDeTalla(1), caballero.ajusteDeTalla());
    assertEquals(Optional.of("Tenis estilo Nike para caballero"), caballero.titulo());
    assertEquals(dama.publicacionId(), caballero.publicacionId());
    assertEquals(dama.fotosDescartadas(), caballero.fotosDescartadas());
    assertTrue(caballero.alertas().contains(AlertaBorrador.TALLAS_CORRIDAS));
    assertEquals(
        Tallas.lista(List.of("35", "36", "38", "41", "42")), dama.tallas(), "la de dama no cambia");
    assertFalse(caballero.id().equals(dama.id()));
  }

  @Test
  void sinAjusteNoCambiaNada() {
    BorradorProducto polo = borrador(TipoProductoProveedor.POLO, Tallas.lista(List.of("M", "L")));

    BorradorProducto.conTallasCorridas(polo, AjusteDeTalla.NINGUNO);

    assertEquals(Tallas.lista(List.of("M", "L")), polo.tallas());
    assertTrue(polo.alertas().isEmpty());
  }
}
