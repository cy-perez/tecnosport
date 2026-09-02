package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import java.util.List;
import java.util.Objects;

public final class ListarCategorias {

  private final RepositorioCategorias repositorioCategorias;

  public ListarCategorias(RepositorioCategorias repositorioCategorias) {
    this.repositorioCategorias =
        Objects.requireNonNull(
            repositorioCategorias, "El repositorio de categorías no puede ser nulo.");
  }

  public List<Categoria> ejecutar() {
    return repositorioCategorias.listarTodas();
  }
}
