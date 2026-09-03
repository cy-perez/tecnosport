package co.tecnosport.api.application.usuario;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.usuario.SesionRefresco;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CerrarSesionTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration VIGENCIA = Duration.ofDays(30);

  @Test
  void cerrarSesionRevocaTodaLaFamilia() {
    RepositorioSesionesFalso sesiones = new RepositorioSesionesFalso();
    CerrarSesion caso = new CerrarSesion(sesiones, new RelojFalso(AHORA));
    UUID usuarioId = UUID.randomUUID();
    UUID familiaId = UUID.randomUUID();
    SesionRefresco primera = SesionRefresco.crear(usuarioId, familiaId, AHORA, VIGENCIA);
    SesionRefresco segunda =
        SesionRefresco.crear(usuarioId, familiaId, AHORA.plusSeconds(1), VIGENCIA);
    sesiones.guardar(primera);
    sesiones.guardar(segunda);

    caso.ejecutar(new CerrarSesionComando(primera.id()));

    assertTrue(sesiones.buscarPorId(primera.id()).orElseThrow().revocadoEn().isPresent());
    assertTrue(sesiones.buscarPorId(segunda.id()).orElseThrow().revocadoEn().isPresent());
  }

  @Test
  void cerrarUnaSesionInexistenteEsInofensivo() {
    RepositorioSesionesFalso sesiones = new RepositorioSesionesFalso();
    CerrarSesion caso = new CerrarSesion(sesiones, new RelojFalso(AHORA));

    assertDoesNotThrow(() -> caso.ejecutar(new CerrarSesionComando(UUID.randomUUID())));
  }
}
