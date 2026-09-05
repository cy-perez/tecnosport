package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VarianteJpaRepository extends JpaRepository<VarianteJpaEntity, UUID> {

  List<VarianteJpaEntity> findByProductoIdIn(Collection<UUID> productoIds);

  boolean existsBySku(String sku);
}
