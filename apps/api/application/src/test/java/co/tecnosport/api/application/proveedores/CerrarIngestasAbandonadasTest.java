package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lo que una instancia muerta dejó en las manos, sin tocar lo que una viva está procesando. */
class CerrarIngestasAbandonadasTest {

  private static final Instant AHORA = Instant.parse("2026-10-11T14:43:00Z");
  private static final Duration TOLERANCIA = Duration.ofMinutes(3);

  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final CerrarIngestasAbandonadas cerrar =
      new CerrarIngestasAbandonadas(lotes, new RelojFalso(AHORA), TOLERANCIA);

  private LoteIngesta tomadoConLatido(Instant ultimoLatido) {
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(
            UUID.randomUUID(), "p/" + UUID.randomUUID() + ".zip", AHORA.minusSeconds(3600));
    lote.iniciar(AHORA.minusSeconds(1800));
    lote.latir(ultimoLatido);
    lotes.guardar(lote);
    return lote;
  }

  @Test
  void cierraElQueDejoDeLatirYDejaElQueLate() {
    LoteIngesta muerto = tomadoConLatido(AHORA.minus(TOLERANCIA).minusSeconds(1));
    LoteIngesta vivo = tomadoConLatido(AHORA.minusSeconds(30));

    int cerrados = cerrar.ejecutar();

    assertEquals(1, cerrados);
    assertEquals(EstadoLote.ERROR, muerto.estado());
    assertEquals(CerrarIngestasAbandonadas.MOTIVO_ABANDONADO, muerto.detalleError().orElseThrow());
    assertEquals(EstadoLote.PROCESANDO, vivo.estado());
  }

  /** En la cola nadie late todavía; si se cerrara, ninguna subida pasaría de la cola. */
  @Test
  void loQueEsperaEnLaColaNoSeCierraPorViejoQueSea() {
    LoteIngesta enCola =
        LoteIngesta.recibirExportacion(
            UUID.randomUUID(), "p/viejo.zip", AHORA.minus(Duration.ofHours(8)));
    lotes.guardar(enCola);

    assertEquals(0, cerrar.ejecutar());
    assertEquals(EstadoLote.RECIBIDO, enCola.estado());
  }

  /**
   * Decide sobre la copia releída con bloqueo, no sobre la de la lista: el último latido pudo
   * llegar entre las dos lecturas.
   */
  @Test
  void decideSobreElLoteReleidoYNoSobreElDeLaLista() {
    lotes.devolverCopias = true;
    LoteIngesta lote = tomadoConLatido(AHORA.minus(TOLERANCIA).minusSeconds(1));
    lotes.antesDeBloquear = () -> lotes.registrarLatido(lote.id(), AHORA.minusSeconds(5));

    int cerrados = cerrar.ejecutar();

    assertEquals(0, cerrados);
    assertEquals(EstadoLote.PROCESANDO, lotes.buscarPorId(lote.id()).orElseThrow().estado());
  }

  @Test
  void unaToleranciaNulaNoSeAcepta() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CerrarIngestasAbandonadas(lotes, new RelojFalso(AHORA), Duration.ZERO));
  }
}
