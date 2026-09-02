package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import java.util.List;

/** Puerto de categorías. Implementación de producción: JPA con PostgreSQL. */
public interface RepositorioCategorias {

  /** Todas las categorías, sin filtrar por línea: quien llama agrupa o filtra si lo necesita. */
  List<Categoria> listarTodas();
}
