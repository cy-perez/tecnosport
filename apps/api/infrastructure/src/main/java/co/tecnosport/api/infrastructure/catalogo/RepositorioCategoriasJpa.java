package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class RepositorioCategoriasJpa implements RepositorioCategorias {

  private final CategoriaJpaRepository categoriaJpaRepository;

  public RepositorioCategoriasJpa(CategoriaJpaRepository categoriaJpaRepository) {
    this.categoriaJpaRepository = Objects.requireNonNull(categoriaJpaRepository);
  }

  @Override
  public List<Categoria> listarTodas() {
    return categoriaJpaRepository.findAll(Sort.by("nombre")).stream()
        .map(this::aCategoria)
        .toList();
  }

  @Override
  public Optional<Categoria> buscarPorId(UUID id) {
    return categoriaJpaRepository.findById(id).map(this::aCategoria);
  }

  private Categoria aCategoria(CategoriaJpaEntity c) {
    return new Categoria(
        c.getId(), c.getNombre(), new Slug(c.getSlug()), LineaCatalogo.valueOf(c.getLinea()));
  }
}
