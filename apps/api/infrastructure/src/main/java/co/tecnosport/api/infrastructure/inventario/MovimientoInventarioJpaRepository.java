package co.tecnosport.api.infrastructure.inventario;

import co.tecnosport.api.infrastructure.inventario.entidad.MovimientoInventarioJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoInventarioJpaRepository
    extends JpaRepository<MovimientoInventarioJpaEntity, UUID> {

  List<MovimientoInventarioJpaEntity> findByInventarioId(UUID inventarioId);
}
