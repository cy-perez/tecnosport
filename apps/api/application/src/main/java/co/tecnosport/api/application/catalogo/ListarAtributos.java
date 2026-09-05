package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Atributo;
import java.util.List;
import java.util.Objects;

public final class ListarAtributos {

  private final RepositorioAtributos repositorioAtributos;

  public ListarAtributos(RepositorioAtributos repositorioAtributos) {
    this.repositorioAtributos =
        Objects.requireNonNull(
            repositorioAtributos, "El repositorio de atributos no puede ser nulo.");
  }

  public List<Atributo> ejecutar() {
    return repositorioAtributos.listarTodas();
  }
}
