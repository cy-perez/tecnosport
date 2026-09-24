package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md.
 *
 * <p>Guarda una sola lista, y eso es nuevo: hasta el 24 de septiembre de 2026 tenía dos —todas y
 * las que tenían productos publicados— porque el puerto tenía dos métodos de listado. Desde que la
 * vitrina muestra el árbol completo solo queda uno, y el doble se simplificó con él.
 *
 * <p>{@code conProductos} es un conjunto de ids aparte y no una lista de categorías: lo que las
 * reglas del árbol preguntan es "¿cuelga algo de esta?", y darle al doble la forma de la pregunta
 * evita que cada prueba tenga que montar productos que no está probando.
 */
final class RepositorioCategoriasFalso implements RepositorioCategorias {

  private final List<Categoria> todas = new ArrayList<>();
  private final List<UUID> conProductos = new ArrayList<>();

  void conCategorias(Categoria... categorias) {
    todas.clear();
    todas.addAll(List.of(categorias));
  }

  /** Marca esas categorías como "tiene productos colgando", sin montar ningún producto. */
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
