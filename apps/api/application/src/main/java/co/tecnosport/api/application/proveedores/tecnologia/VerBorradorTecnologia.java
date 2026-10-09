package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import java.util.Objects;
import java.util.UUID;

/** Un borrador de tecnología, en cualquier estado. */
public final class VerBorradorTecnologia {

  private final RepositorioBorradoresTecnologia repositorio;

  public VerBorradorTecnologia(RepositorioBorradoresTecnologia repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  public BorradorTecnologia ejecutar(UUID id) {
    return repositorio.buscarPorId(id).orElseThrow(() -> new BorradorNoEncontradoException(id));
  }
}
