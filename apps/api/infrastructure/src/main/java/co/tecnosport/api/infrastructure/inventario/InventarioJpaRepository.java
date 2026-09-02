package co.tecnosport.api.infrastructure.inventario;

import co.tecnosport.api.infrastructure.inventario.entidad.InventarioJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InventarioJpaRepository extends JpaRepository<InventarioJpaEntity, UUID> {

  /**
   * Bloqueo pesimista sobre la fila de inventario: dos compradores simultáneos de la última unidad
   * de una variante serializan aquí, no en la variante misma (docs/02-modelo-datos.md).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<InventarioJpaEntity> findByVarianteId(UUID varianteId);
}
