package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de categorías. Implementación de producción: JPA con PostgreSQL. */
public interface RepositorioCategorias {

  /** Todas las categorías, sin filtrar por línea: quien llama agrupa o filtra si lo necesita. */
  List<Categoria> listarTodas();

  Optional<Categoria> buscarPorId(UUID id);
}
