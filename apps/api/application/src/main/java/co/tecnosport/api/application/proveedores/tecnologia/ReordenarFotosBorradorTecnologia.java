package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.proveedores.BorradorNoEditableException;
import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cambia el orden de las fotos del borrador desde el panel: la primera es la principal, la portada
 * del producto al aprobar. Las fotos no cambian, así que no se toca el bucket.
 */
public final class ReordenarFotosBorradorTecnologia {

  private final RepositorioBorradoresTecnologia repositorio;

  public ReordenarFotosBorradorTecnologia(RepositorioBorradoresTecnologia repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  public BorradorTecnologia ejecutar(UUID id, List<UUID> fotoIds) {
    BorradorTecnologia borrador =
        repositorio
            .buscarPorIdParaActualizar(id)
            .orElseThrow(() -> new BorradorNoEncontradoException(id));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    borrador.reordenarFotos(Objects.requireNonNull(fotoIds));
    repositorio.actualizar(borrador);
    return borrador;
  }
}
