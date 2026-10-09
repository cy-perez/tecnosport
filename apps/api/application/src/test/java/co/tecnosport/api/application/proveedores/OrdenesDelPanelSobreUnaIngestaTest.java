package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link PausarIngesta}, {@link ReanudarIngesta} y {@link DetenerIngesta}: lo que pide el panel.
 */
class OrdenesDelPanelSobreUnaIngestaTest {

  private static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");

  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final RelojFalso reloj = new RelojFalso(AHORA);

  private LoteIngesta enCola() {
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(UUID.randomUUID(), "p/a.zip", AHORA.minusSeconds(60));
    lotes.guardar(lote);
    return lote;
  }

  private LoteIngesta enCurso() {
    LoteIngesta lote = enCola();
    lote.iniciar(AHORA.minusSeconds(30));
    return lote;
  }

  @Test
  void pausarYReanudarGuardanElEstadoBloqueandoLaFila() {
    LoteIngesta lote = enCurso();

    LoteIngesta pausado = new PausarIngesta(lotes).ejecutar(lote.id());
    assertEquals(EstadoLote.PAUSADO, pausado.estado());
    assertEquals(EstadoLote.PAUSADO, lotes.buscarPorId(lote.id()).orElseThrow().estado());

    LoteIngesta reanudado = new ReanudarIngesta(lotes).ejecutar(lote.id());
    assertEquals(EstadoLote.PROCESANDO, reanudado.estado());
    assertEquals(2, lotes.bloqueos);
    assertEquals(2, lotes.actualizaciones);
  }

  @Test
  void detenerEnLaColaCierraYEnCursoDejaLaOrden() {
    LoteIngesta enCola = enCola();
    LoteIngesta enCurso = enCurso();
    DetenerIngesta detener = new DetenerIngesta(lotes, reloj);

    assertEquals(EstadoLote.DETENIDO, detener.ejecutar(enCola.id()).estado());
    assertEquals(EstadoLote.DETENIENDO, detener.ejecutar(enCurso.id()).estado());
  }

  /** Una orden que ya no corresponde al estado no se guarda, y dice en qué estado quedó. */
  @Test
  void unaOrdenFueraDeLugarNoEscribeNada() {
    LoteIngesta lote = enCola();
    LoteIngesta terminado = enCurso();
    terminado.terminar(ResumenIngesta.vacio(), AHORA);

    assertThrows(
        LoteEnOtroEstadoException.class, () -> new PausarIngesta(lotes).ejecutar(lote.id()));
    assertThrows(
        LoteEnOtroEstadoException.class, () -> new ReanudarIngesta(lotes).ejecutar(lote.id()));
    LoteEnOtroEstadoException error =
        assertThrows(
            LoteEnOtroEstadoException.class,
            () -> new DetenerIngesta(lotes, reloj).ejecutar(terminado.id()));
    assertTrue(error.getMessage().contains("TERMINADO"), error.getMessage());
    assertEquals(0, lotes.actualizaciones);
  }

  @Test
  void sinLoteEsNoEncontrado() {
    UUID inexistente = UUID.randomUUID();

    assertThrows(
        LoteNoEncontradoException.class, () -> new PausarIngesta(lotes).ejecutar(inexistente));
    assertThrows(
        LoteNoEncontradoException.class,
        () -> new DetenerIngesta(lotes, reloj).ejecutar(inexistente));
  }
}
