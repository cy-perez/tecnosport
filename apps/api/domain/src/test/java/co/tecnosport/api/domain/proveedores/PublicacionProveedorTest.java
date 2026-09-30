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

  private static MensajeProveedor principal() {
    return MensajeProveedor.texto(
        PROVEEDOR, LOTE, new IdExternoDeMensaje("p"), T, "Bolso 💰 53.000");
  }

  @Test
  void nacePendienteConElPrincipalYSinMedios() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(principal());

    assertEquals(EstadoPublicacionProveedor.PENDIENTE_EXTRACCION, publicacion.estado());
    assertEquals(List.of(), publicacion.medios());
    assertEquals(Optional.empty(), publicacion.motivo());
    assertEquals(PROVEEDOR, publicacion.proveedorId());
    assertEquals(LOTE, publicacion.loteId());
  }

  @Test
  void extraerDescartarYFallarSoloDesdePendiente() {
    PublicacionProveedor extraida = PublicacionProveedor.abrir(principal());
    extraida.marcarExtraida();
    assertEquals(EstadoPublicacionProveedor.EXTRAIDA, extraida.estado());
    assertThrows(ExcepcionDeDominio.class, () -> extraida.descartar("no es producto"));
    assertThrows(ExcepcionDeDominio.class, extraida::marcarExtraida);

    PublicacionProveedor descartada = PublicacionProveedor.abrir(principal());
    descartada.descartar("Es un saludo.");
    assertEquals(EstadoPublicacionProveedor.DESCARTADA, descartada.estado());
    assertEquals(Optional.of("Es un saludo."), descartada.motivo());
    assertThrows(ExcepcionDeDominio.class, () -> descartada.fallar("x"));

    PublicacionProveedor rota = PublicacionProveedor.abrir(principal());
    rota.fallar("El extractor no respondió.");
    assertEquals(EstadoPublicacionProveedor.ERROR, rota.estado());
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

    assertThrows(ExcepcionDeDominio.class, () -> publicacion.descartar(" "));
    assertThrows(ExcepcionDeDominio.class, () -> publicacion.fallar(null));
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
                null));
  }
}
