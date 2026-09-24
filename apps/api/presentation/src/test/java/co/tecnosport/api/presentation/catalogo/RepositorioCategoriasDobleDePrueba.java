package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de categorías para las pruebas de controlador, escrito a mano (docs/06-testing.md).
 *
 * <p>Estaba copiado como clase anidada en {@code CategoriaControladorTest}, {@code
 * AdminCategoriaControladorTest} y {@code AdminProductoControladorTest}, con tres implementaciones
 * distintas del mismo puerto. Al ganar el puerto los cinco métodos del árbol había que escribirlos
 * tres veces, y ese fue el momento de juntarlos: tres dobles del mismo puerto que divergen es la
 * forma barata de que una prueba pase por un motivo que la de al lado no admite.
 */
class RepositorioCategoriasDobleDePrueba implements RepositorioCategorias {

  private final List<Categoria> todas = new ArrayList<>();
  private final List<UUID> conProductos = new ArrayList<>();

  void conCategorias(Categoria... categorias) {
    todas.clear();
    todas.addAll(List.of(categorias));
  }

  void conProductosEn(Categoria... categorias) {
    conProductos.clear();
    for (Categoria categoria : categorias) {
      conProductos.add(categoria.id());
    }
  }

  @Override
  public List<Categoria> listarTodas() {
    return List.copyOf(todas);
  }

  @Override
  public Optional<Categoria> buscarPorId(UUID id) {
    return todas.stream().filter(categoria -> categoria.id().equals(id)).findFirst();
  }

  @Override
  public Optional<Categoria> buscarPorSlug(Slug slug) {
    return todas.stream().filter(categoria -> categoria.slug().equals(slug)).findFirst();
  }

  @Override
  public List<Categoria> hijasDe(UUID padreId) {
    return todas.stream()
        .filter(categoria -> categoria.padreId().filter(padreId::equals).isPresent())
        .toList();
  }

  @Override
  public void guardar(Categoria categoria) {
    todas.removeIf(otra -> otra.id().equals(categoria.id()));
    todas.add(categoria);
  }

  @Override
  public void eliminar(UUID id) {
    todas.removeIf(categoria -> categoria.id().equals(id));
  }

  @Override
  public boolean tieneProductos(UUID categoriaId) {
    return conProductos.contains(categoriaId);
  }
}
