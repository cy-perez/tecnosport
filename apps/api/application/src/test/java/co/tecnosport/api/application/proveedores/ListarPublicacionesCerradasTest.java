package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListarPublicacionesCerradasTest {

  private static Optional<MensajeProveedor> conTexto(String texto) {
    return Optional.of(
        MensajeProveedor.texto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new IdExternoDeMensaje("m"),
            Instant.parse("2026-10-11T19:30:00Z"),
            texto));
  }

  @Test
  void elExtractoJuntaLosRenglonesYNoParteUnEmoji() {
    String largo = "👕".repeat(ListarPublicacionesCerradas.LARGO_DEL_EXTRACTO + 5);

    String extracto = ListarPublicacionesCerradas.extracto(conTexto(largo));

    assertTrue(extracto.endsWith("…"));
    assertEquals(
        ListarPublicacionesCerradas.LARGO_DEL_EXTRACTO + 1,
        extracto.codePointCount(0, extracto.length()));
    assertTrue(Character.isSurrogatePair(extracto.charAt(0), extracto.charAt(1)));
    assertEquals(
        "Camiseta polo 1.1 $55.000",
        ListarPublicacionesCerradas.extracto(conTexto("Camiseta polo 1.1\n\n  $55.000 ")));
  }

  @Test
  void unAnuncioDeSoloFotosNoTieneExtracto() {
    assertNull(ListarPublicacionesCerradas.extracto(Optional.empty()));
  }
}
