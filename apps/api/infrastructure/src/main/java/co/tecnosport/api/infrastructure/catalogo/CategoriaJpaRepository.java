package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoriaJpaRepository extends JpaRepository<CategoriaJpaEntity, UUID> {

  Optional<CategoriaJpaEntity> findBySlug(String slug);

  /** Las hijas directas de una categoría del árbol. */
  List<CategoriaJpaEntity> findByPadreIdOrderByNombreAsc(UUID padreId);

  /**
   * ¿Cuelga algún producto de esta categoría? Sin filtrar por estado a propósito: un borrador
   * también se rompe si le borran la categoría por debajo.
   *
   * <p>{@code findConProductosEnEstado} vivía aquí y se fue el 24 de septiembre de 2026 con el
   * árbol de categorías: servía para esconder de la vitrina las categorías vacías, y un menú que se
   * salta "Faldas" porque hoy no hay ninguna le dice al comprador que no vendemos faldas. El
   * equivalente de marcas sí sigue en pie — una marca sin productos no es una promesa de surtido.
   */
  @Query(
      "select case when count(p) > 0 then true else false end "
          + "from ProductoJpaEntity p where p.categoriaId = :categoriaId")
  boolean existeProductoEnCategoria(@Param("categoriaId") UUID categoriaId);
}
