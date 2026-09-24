package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Baja de una categoría, solo si no arrastra nada.
 *
 * <p>Las dos comprobaciones son lecturas previas y no confianza en la llave foránea, y esa es la
 * diferencia entre un mensaje que el panel puede explicar —"tiene 9 subcategorías"— y un {@code
 * 500} con una violación de integridad dentro. La foránea sigue estando debajo y sigue siendo la
 * última palabra si dos peticiones corren a la vez; lo que no puede ser es la primera.
 *
 * <p>No hay borrado en cascada. Ver {@link CategoriaConHijasException}.
 */
public final class EliminarCategoria {

  private final RepositorioCategorias repositorioCategorias;

  public EliminarCategoria(RepositorioCategorias repositorioCategorias) {
    this.repositorioCategorias =
        Objects.requireNonNull(
            repositorioCategorias, "El repositorio de categorías no puede ser nulo.");
  }

  public void ejecutar(UUID id) {
    Categoria categoria =
        repositorioCategorias
            .buscarPorId(id)
            .orElseThrow(() -> new CategoriaNoEncontradaException(id));

    List<Categoria> hijas = repositorioCategorias.hijasDe(id);
    if (!hijas.isEmpty()) {
      throw new CategoriaConHijasException(categoria.nombre(), hijas.size());
    }
    if (repositorioCategorias.tieneProductos(id)) {
      throw new CategoriaConProductosException(categoria.nombre(), "no se puede borrar");
    }

    repositorioCategorias.eliminar(id);
  }
}
