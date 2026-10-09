package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.List;
import java.util.Objects;

/** Los borradores de tecnología en un estado, del más reciente al más viejo. */
public final class ListarBorradoresTecnologia {

  private final RepositorioBorradoresTecnologia repositorio;

  public ListarBorradoresTecnologia(RepositorioBorradoresTecnologia repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  public List<BorradorTecnologia> ejecutar(EstadoBorrador estado) {
    return repositorio.listarPorEstado(Objects.requireNonNull(estado));
  }
}
