package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. Las dos listas van separadas
 * por el mismo motivo que en {@code RepositorioMarcasFalso}.
 */
final class RepositorioCategoriasFalso implements RepositorioCategorias {

  private List<Categoria> todas = List.of();
  private List<Categoria> conProductos = List.of();

  void conCategorias(Categoria... categorias) {
    this.todas = List.of(categorias);
  }

  void conCategoriasConProductosPublicados(Categoria... categorias) {
    this.conProductos = List.of(categorias);
  }

  @Override
  public List<Categoria> listarTodas() {
    return todas;
  }

  @Override
  public List<Categoria> listarConProductosPublicados() {
    return conProductos;
  }

  @Override
  public Optional<Categoria> buscarPorId(UUID id) {
    return todas.stream().filter(categoria -> categoria.id().equals(id)).findFirst();
  }
}
