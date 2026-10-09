package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;
import java.util.UUID;

/**
 * Devuelve a {@code PROCESANDO} un lote pausado. El trabajador lo nota en su próxima mirada —unos
 * segundos, lo que dure {@link EsperaDeIngesta}— y sigue desde la publicación donde se quedó: el
 * hilo nunca lo soltó.
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
