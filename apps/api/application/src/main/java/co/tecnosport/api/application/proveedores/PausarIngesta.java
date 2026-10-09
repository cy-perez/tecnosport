package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;
import java.util.UUID;

/**
 * Pide al trabajador que espere entre una publicación y otra. <b>Retiene la cola</b>: el hilo es
 * uno solo ({@code EjecutorDeIngestasEnHilo}), así que los lotes que esperan detrás esperan
 * también, hasta que este se reanude o se detenga.
 *
 * <p>Corre dentro de la transacción de quien llama, y lee el lote bloqueándolo: el trabajador puede
 * estar escribiendo el final en ese mismo instante, y sin el bloqueo una de las dos escrituras
 * pisaría a la otra en silencio.
 */
public final class PausarIngesta {

  private final RepositorioLotesIngesta repositorioLotes;

  public PausarIngesta(RepositorioLotesIngesta repositorioLotes) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
  }

  public LoteIngesta ejecutar(UUID loteId) {
    Objects.requireNonNull(loteId, "El id no puede ser nulo.");
    LoteIngesta lote =
        repositorioLotes
            .buscarPorIdParaActualizar(loteId)
            .orElseThrow(() -> new LoteNoEncontradoException(loteId));
    try {
      lote.pausar();
    } catch (ExcepcionDeDominio e) {
      throw new LoteEnOtroEstadoException(lote.estado());
    }
    repositorioLotes.actualizar(lote);
    return lote;
  }
}
