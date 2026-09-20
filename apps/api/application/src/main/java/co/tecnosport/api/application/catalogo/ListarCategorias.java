package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import java.util.List;
import java.util.Objects;

/**
 * Las categorías del filtro de la vitrina: solo las que tienen algo publicado detrás.
 *
 * <p>Desde que {@code V38} dejó la línea de tecnología con once categorías, la vitrina ofrecía
 * "Proyectores" y "Computadores" con la rejilla vacía detrás. El panel usa {@link
 * ListarCategoriasAdmin}, que sí las ve todas — si no, no habría forma de cargar el primer
 * proyector.
 */
public final class ListarCategorias {

  private final RepositorioCategorias repositorioCategorias;

  public ListarCategorias(RepositorioCategorias repositorioCategorias) {
    this.repositorioCategorias =
        Objects.requireNonNull(
            repositorioCategorias, "El repositorio de categorías no puede ser nulo.");
  }

  public List<Categoria> ejecutar() {
    return repositorioCategorias.listarConProductosPublicados();
  }
}
