package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Un reinicio se lleva la cola en memoria; esto es lo que pasa con lo que había en ella. */
class ReanudarLotesDeIngestaTest {

  private static final Instant AHORA = Instant.parse("2026-09-30T12:00:00Z");

  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final UUID proveedorId = UUID.randomUUID();

  /** Un ejecutor que anota lo que le llega y, si se le pide, rechaza todo. */
  private static final class EjecutorAnotador implements EjecutorDeIngestas {
    final List<UUID> encolados = new ArrayList<>();
    boolean lleno;

    @Override
    public void encolar(UUID loteId) {
      if (lleno) {
        throw new ColaDeIngestasLlenaException();
      }
      encolados.add(loteId);
    }
  }

  private LoteIngesta lote(EstadoLote estado) {
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(
            proveedorId, "p/exportaciones/" + UUID.randomUUID() + ".zip", AHORA.minusSeconds(600));
    if (estado != EstadoLote.RECIBIDO) {
      lote.iniciar(AHORA.minusSeconds(500));
    }
    if (estado == EstadoLote.TERMINADO) {
      lote.terminar(new ResumenIngesta(0, 0, 0, 0, 0, 0, 0, 0, 0), AHORA.minusSeconds(400));
    }
    lotes.guardar(lote);
    return lote;
  }

  @Test
  void reencolaLosRecibidosYFallaLosQueIbanAMedias() {
    LoteIngesta enCola = lote(EstadoLote.RECIBIDO);
    LoteIngesta aMedias = lote(EstadoLote.PROCESANDO);
    LoteIngesta terminado = lote(EstadoLote.TERMINADO);
    EjecutorAnotador ejecutor = new EjecutorAnotador();

    ReanudarLotesDeIngesta.Resultado resultado =
        new ReanudarLotesDeIngesta(lotes, ejecutor, new RelojFalso(AHORA)).ejecutar();

    assertEquals(new ReanudarLotesDeIngesta.Resultado(1, 1), resultado);
    assertEquals(List.of(enCola.id()), ejecutor.encolados);
    assertEquals(EstadoLote.ERROR, aMedias.estado());
    assertEquals(ReanudarLotesDeIngesta.MOTIVO_REINICIO, aMedias.detalleError().orElseThrow());
    assertEquals(EstadoLote.TERMINADO, terminado.estado(), "lo cerrado no se toca");
  }

  /** Si la cola no lo acepta tampoco al reanudar, el lote no se queda en RECIBIDO sin dueño. */
  @Test
  void loQueLaColaRechazaAlReanudarQuedaEnErrorConSuMotivo() {
    LoteIngesta enCola = lote(EstadoLote.RECIBIDO);
    EjecutorAnotador ejecutor = new EjecutorAnotador();
    ejecutor.lleno = true;

    ReanudarLotesDeIngesta.Resultado resultado =
        new ReanudarLotesDeIngesta(lotes, ejecutor, new RelojFalso(AHORA)).ejecutar();

    assertEquals(new ReanudarLotesDeIngesta.Resultado(0, 1), resultado);
    assertEquals(EstadoLote.ERROR, enCola.estado());
  }

  /** El hilo que los retenía se fue con el reinicio: no hay quién los reanude ni los suelte. */
  @Test
  void losPausadosYLosQueSeDeteniaSeCierranComoLosQueIbanAMedias() {
    LoteIngesta pausado = lote(EstadoLote.PROCESANDO);
    pausado.pausar();
    LoteIngesta deteniendo = lote(EstadoLote.PROCESANDO);
    deteniendo.pedirDetencion(AHORA);
    EjecutorAnotador ejecutor = new EjecutorAnotador();

    ReanudarLotesDeIngesta.Resultado resultado =
        new ReanudarLotesDeIngesta(lotes, ejecutor, new RelojFalso(AHORA)).ejecutar();

    assertEquals(new ReanudarLotesDeIngesta.Resultado(0, 2), resultado);
    assertEquals(EstadoLote.ERROR, pausado.estado());
    assertEquals(EstadoLote.ERROR, deteniendo.estado());
  }
}
