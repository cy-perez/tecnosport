package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;
import java.util.UUID;

/**
 * Devuelve a la cola un lote pausado. En pausa nadie lo tiene —el trabajador lo soltó al verla—,
 * así que <b>quien llama tiene que encolarlo</b> después de confirmar ({@link EncolarIngesta}); el
 * tramo que lo tome sigue desde la publicación pendiente (ADR-0080).
 *
 * <p>No confundir con {@link ReanudarLotesDeIngesta}, que es lo que hace el arranque con la cola
 * que se llevó un reinicio.
 */
public final class ReanudarIngesta {

  private final RepositorioLotesIngesta repositorioLotes;

  public ReanudarIngesta(RepositorioLotesIngesta repositorioLotes) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
  }

  public LoteIngesta ejecutar(UUID loteId) {
    Objects.requireNonNull(loteId, "El id no puede ser nulo.");
    LoteIngesta lote =
        repositorioLotes
            .buscarPorIdParaActualizar(loteId)
            .orElseThrow(() -> new LoteNoEncontradoException(loteId));
    try {
      lote.reanudar();
    } catch (ExcepcionDeDominio e) {
      throw new LoteEnOtroEstadoException(lote.estado());
    }
    repositorioLotes.actualizar(lote);
    return lote;
  }
}
