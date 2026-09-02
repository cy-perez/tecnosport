package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.SetRotacionJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SetRotacionJpaRepository extends JpaRepository<SetRotacionJpaEntity, UUID> {

  List<SetRotacionJpaEntity> findByProductoIdIn(Collection<UUID> productoIds);
}
