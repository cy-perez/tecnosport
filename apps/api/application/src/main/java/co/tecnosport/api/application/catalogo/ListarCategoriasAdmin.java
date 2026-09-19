package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import java.util.List;
import java.util.Objects;

/**
 * Todas las categorías, tengan productos o no, para el formulario del panel.
 *
 * <p>Mismo motivo que {@link ListarMarcasAdmin}: es la única forma de cargar el primer producto de
 * una categoría que hoy está vacía, y son casi todas las que {@code V38} dio de alta.
 */
public final class ListarCategoriasAdmin {

  private final RepositorioCategorias repositorioCategorias;

  public ListarCategoriasAdmin(RepositorioCategorias repositorioCategorias) {
    this.repositorioCategorias =
        Objects.requireNonNull(
            repositorioCategorias, "El repositorio de categorías no puede ser nulo.");
  }

  public List<Categoria> ejecutar() {
    return repositorioCategorias.listarTodas();
  }
}
