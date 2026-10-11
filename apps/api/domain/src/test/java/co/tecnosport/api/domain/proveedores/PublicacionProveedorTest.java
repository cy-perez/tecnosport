package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicacionProveedorTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");
  private static final UUID PROVEEDOR = UUID.randomUUID();
  private static final UUID LOTE = UUID.randomUUID();
  private static final CierreDePublicacion SALUDO =
      CierreDePublicacion.de(MotivoDePublicacion.SIN_PRODUCTO);
  private static final CierreDePublicacion ROTA =
      CierreDePublicacion.de(MotivoDePublicacion.EXTRACCION_FALLIDA);

  private static MensajeProveedor principal() {
    return MensajeProveedor.texto(
        PROVEEDOR, LOTE, new IdExternoDeMensaje("p"), T, "Bolso 💰 53.000");
  }

  @Test
  void nacePendienteConElPrincipalYSinMedios() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(principal());

    assertEquals(EstadoPublicacionProveedor.PENDIENTE_EXTRACCION, publicacion.estado());
    assertEquals(List.of(), publicacion.medios());
    assertEquals(Optional.empty(), publicacion.cierre());
    assertEquals(PROVEEDOR, publicacion.proveedorId());
    assertEquals(LOTE, publicacion.loteId());
  }

  @Test
  void extraerDescartarYFallarSoloDesdePendiente() {
    PublicacionProveedor extraida = PublicacionProveedor.abrir(principal());
    extraida.marcarExtraida();
    assertEquals(EstadoPublicacionProveedor.EXTRAIDA, extraida.estado());
    assertThrows(ExcepcionDeDominio.class, () -> extraida.descartar(SALUDO));
    assertThrows(ExcepcionDeDominio.class, extraida::marcarExtraida);

    PublicacionProveedor descartada = PublicacionProveedor.abrir(principal());
    descartada.descartar(SALUDO);
    assertEquals(EstadoPublicacionProveedor.DESCARTADA, descartada.estado());
    assertEquals(Optional.of(SALUDO), descartada.cierre());
    assertThrows(ExcepcionDeDominio.class, () -> descartada.fallar(ROTA, "x"));

    PublicacionProveedor rota = PublicacionProveedor.abrir(principal());
    rota.fallar(ROTA, "La API respondió 400: Your credit balance is too low.");
    assertEquals(EstadoPublicacionProveedor.ERROR, rota.estado());
    assertEquals(
        Optional.of("La API respondió 400: Your credit balance is too low."), rota.detalle());
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            rota.anexar(
                MensajeProveedor.texto(
                    PROVEEDOR, LOTE, new IdExternoDeMensaje("t"), T, "otra cosa")));
  }

  @Test
  void descartarYFallarExigenMotivo() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(principal());

    assertThrows(NullPointerException.class, () -> publicacion.descartar(null));
    assertThrows(NullPointerException.class, () -> publicacion.fallar(null, "detalle"));
    assertEquals(EstadoPublicacionProveedor.PENDIENTE_EXTRACCION, publicacion.estado());
  }

  @Test
  void alReconstruirUnaDescartadaSinMotivoNoSeSostiene() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new PublicacionProveedor(
                UUID.randomUUID(),
                PROVEEDOR,
                LOTE,
                UUID.randomUUID(),
                List.of(),
                List.of(),
                T,
                EstadoPublicacionProveedor.DESCARTADA,
                null,
                "Es un saludo."));
  }

  /**
   * Reintentar las fallidas las vuelve a poner a esperar al extractor, sin el motivo ni el detalle
   * de la vez anterior. Una descartada no: se leyó y no era un producto.
   */
  @Test
  void soloLaFallidaSeReabre() {
    PublicacionProveedor rota = PublicacionProveedor.abrir(principal());
    rota.fallar(ROTA, "La API respondió 400.");

    rota.reabrir();

    assertEquals(EstadoPublicacionProveedor.PENDIENTE_EXTRACCION, rota.estado());
    assertEquals(Optional.empty(), rota.cierre());
    assertEquals(Optional.empty(), rota.detalle());

    PublicacionProveedor descartada = PublicacionProveedor.abrir(principal());
    descartada.descartar(SALUDO);
    assertThrows(ExcepcionDeDominio.class, descartada::reabrir);
    assertThrows(ExcepcionDeDominio.class, () -> PublicacionProveedor.abrir(principal()).reabrir());
  }

  @Test
  void elDatoDeUnCierreEsCorto() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new CierreDePublicacion(
                MotivoDePublicacion.MISMA_REFERENCIA_EN_REVISION, "x".repeat(201)));
    assertEquals(
        Optional.of("3"),
        new CierreDePublicacion(MotivoDePublicacion.MISMA_REFERENCIA_EN_REVISION, " 3 ")
            .datoOpcional());
  }
}
