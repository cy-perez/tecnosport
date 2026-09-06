package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagenProductoJpaRepository extends JpaRepository<ImagenProductoJpaEntity, UUID> {

  List<ImagenProductoJpaEntity> findByProductoIdIn(Collection<UUID> productoIds);

  /** A lo sumo una fila por producto (constraint única en BD, {@code variante_id is null}). */
  Optional<ImagenProductoJpaEntity> findByProductoIdAndTipoAndVarianteIdIsNull(
      UUID productoId, String tipo);
}
