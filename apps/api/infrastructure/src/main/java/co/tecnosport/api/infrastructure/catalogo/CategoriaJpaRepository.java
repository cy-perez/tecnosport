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

  /** Mismo criterio y mismo porqué que {@code MarcaJpaRepository#findConProductosEnEstado}. */
  @Query(
      "select c from CategoriaJpaEntity c where exists "
          + "(select 1 from ProductoJpaEntity p where p.categoriaId = c.id and p.estado = :estado) "
          + "order by c.nombre")
  List<CategoriaJpaEntity> findConProductosEnEstado(@Param("estado") String estado);
}
