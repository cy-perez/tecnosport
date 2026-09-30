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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BorradorProductoTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");
  private static final UUID PUBLICACION = UUID.randomUUID();
  private static final UUID PROVEEDOR = UUID.randomUUID();
  private static final HuellaProveedor HUELLA =
      HuellaProveedor.calcular(PROVEEDOR, "Bolso de dama mediano", Dinero.deCop(53000));

  private static ProductoExtraido extraido() {
    return new ProductoExtraido(
        true,
        false,
        "Bolso de dama mediano",
        LineaCatalogo.BOLSOS,
        TipoProductoProveedor.BOLSO,
        Dinero.deCop(53000),
        Tallas.desconocida(),
        4,
        List.of(),
        "importado",
        List.of("incluye llavero"),
        new BigDecimal("0.92"),
        null);
  }

  private static BorradorProducto nuevo(Set<AlertaBorrador> alertas) {
    return BorradorProducto.nuevo(
        PUBLICACION,
        PROVEEDOR,
        extraido(),
        "{\"titulo\":\"Bolso de dama mediano\"}",
        Dinero.deCop(53000),
        Dinero.deCop(71600),
        HUELLA,
        null,
        alertas,
        T);
  }

  @Test
  void naceEnRevisionConLoExtraidoYSinProducto() {
    BorradorProducto borrador = nuevo(Set.of());

    assertEquals(EstadoBorrador.EN_REVISION, borrador.estado());
    assertEquals(Optional.of("Bolso de dama mediano"), borrador.titulo());
    assertEquals(Optional.of(Dinero.deCop(71600)), borrador.precioVentaSugerido());
    assertEquals(Optional.of(4), borrador.cantidadTonos());
    assertEquals(Optional.empty(), borrador.productoId());
    assertTrue(borrador.esPublicableAutomaticamente());
  }

  @Test
  void conAlertasNuncaEsPublicableSolo() {
    BorradorProducto borrador = nuevo(Set.of(AlertaBorrador.CONFIANZA_BAJA));

    assertTrue(borrador.tieneAlertas());
    assertFalse(borrador.esPublicableAutomaticamente());
  }

  @Test
  void editarCambiaSoloLoQueLlegaYSoloEnRevision() {
    BorradorProducto borrador = nuevo(Set.of());

    borrador.editar(
        "Bolso mediano ejecutivo", null, Dinero.deCop(75000), null, null, null, null, null);

    assertEquals(Optional.of("Bolso mediano ejecutivo"), borrador.titulo());
    assertEquals(Optional.of(Dinero.deCop(75000)), borrador.precioVentaSugerido());
    assertEquals(TipoProductoProveedor.BOLSO, borrador.tipo());
    assertEquals(Optional.of(4), borrador.cantidadTonos());

    borrador.aprobar(UUID.randomUUID());
    assertThrows(
        ExcepcionDeDominio.class,
        () -> borrador.editar("x", null, null, null, null, null, null, null));
  }

  @Test
  void aprobarApuntaAlProductoYCierraElBorrador() {
    BorradorProducto borrador = nuevo(Set.of());
    UUID producto = UUID.randomUUID();

    borrador.aprobar(producto);

    assertEquals(EstadoBorrador.APROBADO, borrador.estado());
    assertEquals(Optional.of(producto), borrador.productoId());
    assertFalse(borrador.esPublicableAutomaticamente());
    assertThrows(ExcepcionDeDominio.class, () -> borrador.rechazar("tarde"));
    assertThrows(ExcepcionDeDominio.class, () -> borrador.aprobar(UUID.randomUUID()));
  }

  @Test
  void rechazarExigeMotivoYNoCambiaNadaSiFalta() {
    BorradorProducto borrador = nuevo(Set.of());

    assertThrows(ExcepcionDeDominio.class, () -> borrador.rechazar("  "));
    assertEquals(EstadoBorrador.EN_REVISION, borrador.estado());

    borrador.rechazar("Es una promoción, no un producto.");
    assertEquals(EstadoBorrador.RECHAZADO, borrador.estado());
    assertEquals(Optional.of("Es una promoción, no un producto."), borrador.motivoRechazo());
  }

  @Test
  void unaRenovacionNaceCerradaYApuntandoAlProducto() {
    UUID producto = UUID.randomUUID();

    BorradorProducto renovacion =
        BorradorProducto.renovacionAplicada(
            PUBLICACION,
            PROVEEDOR,
            producto,
            extraido(),
            "{}",
            Dinero.deCop(55000),
            HUELLA,
            null,
            Set.of(AlertaBorrador.PRECIO_CAMBIO),
            T);

    assertEquals(EstadoBorrador.RENOVACION_APLICADA, renovacion.estado());
    assertEquals(Optional.of(producto), renovacion.productoId());
    assertEquals(Optional.empty(), renovacion.precioVentaSugerido());
    assertTrue(renovacion.alertas().contains(AlertaBorrador.PRECIO_CAMBIO));
    assertThrows(ExcepcionDeDominio.class, () -> renovacion.aprobar(producto));
  }

  @Test
  void alReconstruirUnAprobadoSinProductoOUnRechazadoSinMotivoNoSeSostiene() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new BorradorProducto(
                UUID.randomUUID(),
                PUBLICACION,
                PROVEEDOR,
                "{}",
                "t",
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
                null,
                null,
                EstadoBorrador.APROBADO,
                null,
                null,
                T));
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new BorradorProducto(
                UUID.randomUUID(),
                PUBLICACION,
                PROVEEDOR,
                "{}",
                "t",
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
                null,
                null,
                EstadoBorrador.RECHAZADO,
                null,
                null,
                T));
  }
}
