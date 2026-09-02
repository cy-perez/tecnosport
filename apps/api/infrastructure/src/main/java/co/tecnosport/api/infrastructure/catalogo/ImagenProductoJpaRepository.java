package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagenProductoJpaRepository extends JpaRepository<ImagenProductoJpaEntity, UUID> {

  List<ImagenProductoJpaEntity> findByProductoIdIn(Collection<UUID> productoIds);
}
