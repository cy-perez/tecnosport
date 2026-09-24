package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.CategoriaSlugYaExisteException;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
        .map(RepositorioCategoriasJpa::aCategoria)
        .toList();
  }

  @Override
  public Optional<Categoria> buscarPorId(UUID id) {
    return categoriaJpaRepository.findById(id).map(RepositorioCategoriasJpa::aCategoria);
  }

  @Override
  public Optional<Categoria> buscarPorSlug(Slug slug) {
    return categoriaJpaRepository
        .findBySlug(slug.valor())
        .map(RepositorioCategoriasJpa::aCategoria);
  }

  @Override
  public List<Categoria> hijasDe(UUID padreId) {
    return categoriaJpaRepository.findByPadreIdOrderByNombreAsc(padreId).stream()
        .map(RepositorioCategoriasJpa::aCategoria)
        .toList();
  }

  /**
   * {@code creadoEn} lo pone la infraestructura y no el dominio, igual que en {@code
   * RepositorioMarcasJpa}: {@link Categoria} no tiene fecha porque ninguna regla de negocio la
   * mira. Al actualizar se conserva la de la fila que ya está — reescribirla con {@code
   * Instant.now()} convertiría un renombrado en un alta.
   *
   * <p>{@code saveAndFlush} y el {@code catch}, por el mismo motivo que las marcas: con {@code
   * save} el {@code INSERT} queda pendiente hasta que Hibernate vuelca al confirmar la transacción,
   * que es <b>fuera</b> de este {@code try}, y la violación saldría del módulo como {@code 500}. La
   * lectura previa de {@code CrearCategoria} atrapa el caso normal; esto atrapa lo que ella no
   * puede ver: dos peticiones que leen "libre" a la vez.
   *
   * <p>Atribuir la violación al slug es seguro: {@code categoria} solo tiene la llave primaria —un
   * UUID v7 recién generado—, el único de {@code slug} y la foránea a sí misma, que quien llama ya
   * validó.
   */
  @Override
  public void guardar(Categoria categoria) {
    Instant creadoEn =
        categoriaJpaRepository
            .findById(categoria.id())
            .map(CategoriaJpaEntity::getCreadoEn)
            .orElseGet(Instant::now);

    try {
      categoriaJpaRepository.saveAndFlush(
          new CategoriaJpaEntity(
              categoria.id(),
              categoria.nombre(),
              categoria.slug().valor(),
              categoria.linea().name(),
              categoria.padreId().orElse(null),
              creadoEn));
    } catch (DataIntegrityViolationException e) {
      throw new CategoriaSlugYaExisteException(categoria.slug().valor());
    }
  }

  @Override
  public void eliminar(UUID id) {
    categoriaJpaRepository.deleteById(id);
  }

  @Override
  public boolean tieneProductos(UUID categoriaId) {
    return categoriaJpaRepository.existeProductoEnCategoria(categoriaId);
  }

  static Categoria aCategoria(CategoriaJpaEntity c) {
    return new Categoria(
        c.getId(),
        c.getNombre(),
        new Slug(c.getSlug()),
        LineaCatalogo.valueOf(c.getLinea()),
        c.getPadreId());
  }
}
