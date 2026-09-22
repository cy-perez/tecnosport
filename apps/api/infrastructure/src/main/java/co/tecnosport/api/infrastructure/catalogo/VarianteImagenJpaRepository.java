package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteImagenJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VarianteImagenJpaRepository extends JpaRepository<VarianteImagenJpaEntity, UUID> {

  /**
   * En lote y no de a una: el hidratador del catálogo trae las imágenes de todos los productos de
   * la página en una consulta, y sus variantes en otra. De a una, una rejilla de veinticuatro
   * productos con galería haría cien consultas.
   */
  List<VarianteImagenJpaEntity> findByImagenIdIn(Collection<UUID> imagenIds);
}
