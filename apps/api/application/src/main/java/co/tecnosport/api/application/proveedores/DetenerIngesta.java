package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;
import java.util.UUID;

/**
 * Detiene un lote para que la cola siga con el siguiente.
 *
 * <p>En la cola, se cierra en el acto. En curso o en pausa, queda {@code DETENIENDO} y el
 * trabajador lo suelta en su siguiente punto de control, con lo que alcanzó a hacer; si estaba en
 * medio de una extracción o subiendo las fotos, eso termina primero. Ver {@link
 * LoteIngesta#pedirDetencion} para lo que <b>no</b> se retoma.
 */
public final class DetenerIngesta {

  private final RepositorioLotesIngesta repositorioLotes;
  private final Reloj reloj;

  public DetenerIngesta(RepositorioLotesIngesta repositorioLotes, Reloj reloj) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public LoteIngesta ejecutar(UUID loteId) {
    Objects.requireNonNull(loteId, "El id no puede ser nulo.");
    LoteIngesta lote =
        repositorioLotes
            .buscarPorIdParaActualizar(loteId)
            .orElseThrow(() -> new LoteNoEncontradoException(loteId));
    try {
      lote.pedirDetencion(reloj.ahora());
    } catch (ExcepcionDeDominio e) {
      throw new LoteEnOtroEstadoException(lote.estado());
    }
    repositorioLotes.actualizar(lote);
    return lote;
  }
}
