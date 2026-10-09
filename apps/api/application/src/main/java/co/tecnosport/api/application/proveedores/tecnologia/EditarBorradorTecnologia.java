package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.proveedores.BorradorNoEditableException;
import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Los colores y el precio de venta que elige quien revisa, configuración por configuración. */
public final class EditarBorradorTecnologia {

  private final RepositorioBorradoresTecnologia repositorio;

  public EditarBorradorTecnologia(RepositorioBorradoresTecnologia repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  public BorradorTecnologia ejecutar(UUID id, List<BorradorTecnologia.Eleccion> elecciones) {
    BorradorTecnologia borrador = enRevision(id);
    borrador.elegir(Objects.requireNonNull(elecciones));
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
