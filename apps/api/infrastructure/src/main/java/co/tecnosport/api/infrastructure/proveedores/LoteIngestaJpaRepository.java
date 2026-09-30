package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.LoteIngestaJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoteIngestaJpaRepository extends JpaRepository<LoteIngestaJpaEntity, UUID> {

  Page<LoteIngestaJpaEntity> findByProveedorId(UUID proveedorId, Pageable pageable);

  List<LoteIngestaJpaEntity> findByEstadoInOrderByCreadoEnAsc(Collection<String> estados);
}
