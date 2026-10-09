package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.proveedores.BorradorNoEditableException;
import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.Objects;
import java.util.UUID;

/**
 * Rechaza un modelo, o las configuraciones nuevas de uno: la siguiente lista no las vuelve a
 * proponer.
 */
public final class RechazarBorradorTecnologia {

  private final RepositorioBorradoresTecnologia repositorio;

  public RechazarBorradorTecnologia(RepositorioBorradoresTecnologia repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  public BorradorTecnologia ejecutar(UUID id, String motivo) {
    BorradorTecnologia borrador = enRevision(id);
    borrador.rechazar(motivo);
    repositorio.actualizar(borrador);
    return borrador;
  }

  private BorradorTecnologia enRevision(UUID id) {
    BorradorTecnologia borrador =
        repositorio
            .buscarPorIdParaActualizar(id)
            .orElseThrow(() -> new BorradorNoEncontradoException(id));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    return borrador;
  }
}
