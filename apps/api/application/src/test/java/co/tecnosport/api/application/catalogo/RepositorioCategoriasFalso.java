package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioCategoriasFalso implements RepositorioCategorias {

  private List<Categoria> categorias = List.of();

  void conCategorias(Categoria... categorias) {
    this.categorias = List.of(categorias);
  }

  @Override
  public List<Categoria> listarTodas() {
    return categorias;
  }

  @Override
  public Optional<Categoria> buscarPorId(UUID id) {
    return categorias.stream().filter(categoria -> categoria.id().equals(id)).findFirst();
  }
}
