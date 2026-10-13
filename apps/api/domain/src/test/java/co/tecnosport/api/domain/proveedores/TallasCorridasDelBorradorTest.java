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

  @Test
  void unaTallaUnicaNoSeCorre() {
    BorradorProducto polo = borrador(TipoProductoProveedor.POLO, Tallas.unica(null));

    BorradorProducto.conTallasCorridas(polo, new AjusteDeTalla(1));

    assertEquals(Tallas.unica(null), polo.tallas());
    assertTrue(polo.alertas().contains(AlertaBorrador.TALLAS_SIN_CORRER));
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

  @Test
  void sinAjusteNoCambiaNada() {
    BorradorProducto polo = borrador(TipoProductoProveedor.POLO, Tallas.lista(List.of("M", "L")));

    BorradorProducto.conTallasCorridas(polo, AjusteDeTalla.NINGUNO);

    assertEquals(Tallas.lista(List.of("M", "L")), polo.tallas());
    assertTrue(polo.alertas().isEmpty());
  }
}
