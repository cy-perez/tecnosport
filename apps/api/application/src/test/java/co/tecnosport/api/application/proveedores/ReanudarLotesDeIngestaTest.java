package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Al arrancar: lo abandonado se retoma, y la cola en memoria se rehace. */
class ReanudarLotesDeIngestaTest {

  private static final Instant AHORA = Instant.parse("2026-09-30T12:00:00Z");
  private static final Duration TOLERANCIA = Duration.ofMinutes(3);

  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final RelojFalso reloj = new RelojFalso(AHORA);
  private final UUID proveedorId = UUID.randomUUID();

  private record Cola(boolean sobrevive) implements EjecutorDeIngestas {
    @Override
    public void encolar(UUID loteId) {
      throw new AssertionError("ReanudarLotesDeIngesta no encola: devuelve qué encolar.");
    }

    @Override
    public boolean sobreviveAUnReinicio() {
      return sobrevive;
    }
  }

  private ReanudarLotesDeIngesta reanudar(boolean colaQueSobrevive) {
    return new ReanudarLotesDeIngesta(
        lotes,
        new RecuperarIngestasAbandonadas(lotes, reloj, TOLERANCIA, null),
        new Cola(colaQueSobrevive));
  }

  /** Los tomados, hace diez minutos y sin latir desde entonces: su instancia murió. */
  private LoteIngesta lote(EstadoLote estado, int segundosAntes) {
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(
            proveedorId,
            "p/exportaciones/" + UUID.randomUUID() + ".zip",
            AHORA.minusSeconds(segundosAntes));
    if (estado != EstadoLote.RECIBIDO) {
      lote.iniciar(AHORA.minusSeconds(600));
    }
    if (estado == EstadoLote.TERMINADO) {
      lote.terminar(ResumenIngesta.vacio(), AHORA.minusSeconds(400));
    }
    lotes.guardar(lote);
    return lote;
  }

  /** Con la cola en memoria: lo de la cola vuelve a ella, en orden, y el abandonado también. */
  @Test
  void conLaColaEnMemoriaRehaceLaColaYRetomaElAbandonado() {
    LoteIngesta aMedias = lote(EstadoLote.PROCESANDO, 900);
    LoteIngesta enCola = lote(EstadoLote.RECIBIDO, 800);
    LoteIngesta terminado = lote(EstadoLote.TERMINADO, 700);

    ReanudarLotesDeIngesta.Resultado resultado = reanudar(false).ejecutar();

    assertEquals(List.of(aMedias.id(), enCola.id()), resultado.porEncolar());
    assertEquals(1, resultado.retomados());
    assertEquals(EstadoLote.RECIBIDO, aMedias.estado(), "vuelve a la cola, no se cierra");
    assertEquals(EstadoLote.TERMINADO, terminado.estado(), "lo cerrado no se toca");
  }

  /**
   * Con Cloud Tasks lo de la cola sigue en la cola de afuera: encolarlo en cada arranque —en dev,
   * muchos al día— solo duplicaría tareas. El abandonado sí, que no tiene tarea.
   */
  @Test
  void conLaColaDeAfueraSoloEncolaElAbandonado() {
    LoteIngesta aMedias = lote(EstadoLote.PROCESANDO, 900);
    lote(EstadoLote.RECIBIDO, 800);

    ReanudarLotesDeIngesta.Resultado resultado = reanudar(true).ejecutar();

    assertEquals(List.of(aMedias.id()), resultado.porEncolar());
  }

  /**
   * El caso del 11 de octubre de 2026: arranca una segunda instancia mientras la primera procesa.
   * El lote late, así que es de alguien vivo: ni se cierra ni se vuelve a encolar.
   */
  @Test
  void elLoteQueOtraInstanciaEstaProcesandoNoSeToca() {
    LoteIngesta deLaVecina = lote(EstadoLote.PROCESANDO, 900);
    deLaVecina.latir(AHORA.minusSeconds(20));

    ReanudarLotesDeIngesta.Resultado resultado = reanudar(true).ejecutar();

    assertEquals(List.of(), resultado.porEncolar());
    assertEquals(EstadoLote.PROCESANDO, deLaVecina.estado());
  }

  /** El que se estaba deteniendo queda detenido, que es lo que se pidió; no vuelve a la cola. */
  @Test
  void elQueSeDeteniaQuedaDetenido() {
    LoteIngesta deteniendo = lote(EstadoLote.PROCESANDO, 900);
    deteniendo.pedirDetencion(AHORA);

    ReanudarLotesDeIngesta.Resultado resultado = reanudar(true).ejecutar();

    assertEquals(EstadoLote.DETENIDO, deteniendo.estado());
    assertEquals(1, resultado.cerrados());
  }
}
