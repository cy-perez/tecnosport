package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.proveedores.BorradorNoEditableException;
import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.Objects;
import java.util.UUID;

/**
 * Corrige o recorta la ficha que preparó la skill antes de aprobar (23/10/2026). Un texto en blanco
 * o nulo vuelve a la de la skill. La edición sobrevive a las listas siguientes: ver {@link
 * BorradorTecnologia#editarDescripcion(String)}.
 */
public final class EditarFichaBorradorTecnologia {

  private final RepositorioBorradoresTecnologia repositorio;

  public EditarFichaBorradorTecnologia(RepositorioBorradoresTecnologia repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  public BorradorTecnologia ejecutar(UUID id, String descripcion) {
    BorradorTecnologia borrador =
        repositorio
            .buscarPorIdParaActualizar(id)
            .orElseThrow(() -> new BorradorNoEncontradoException(id));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    borrador.editarDescripcion(descripcion);
    repositorio.actualizar(borrador);
    return borrador;
  }
}
