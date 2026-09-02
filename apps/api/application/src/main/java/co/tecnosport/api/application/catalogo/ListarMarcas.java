package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Marca;
import java.util.List;
import java.util.Objects;

public final class ListarMarcas {

  private final RepositorioMarcas repositorioMarcas;

  public ListarMarcas(RepositorioMarcas repositorioMarcas) {
    this.repositorioMarcas =
        Objects.requireNonNull(repositorioMarcas, "El repositorio de marcas no puede ser nulo.");
  }

  public List<Marca> ejecutar() {
    return repositorioMarcas.listarTodas();
  }
}
