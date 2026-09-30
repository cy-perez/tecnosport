package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.BorradorProductoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorradorProductoJpaRepository
    extends JpaRepository<BorradorProductoJpaEntity, UUID> {

  Page<BorradorProductoJpaEntity> findByEstado(String estado, Pageable pageable);

  Page<BorradorProductoJpaEntity> findByProveedorId(UUID proveedorId, Pageable pageable);

  Page<BorradorProductoJpaEntity> findByEstadoAndProveedorId(
      String estado, UUID proveedorId, Pageable pageable);

  List<BorradorProductoJpaEntity> findByProveedorIdAndProductoIdIsNotNullAndPhashIsNotNull(
      UUID proveedorId);
}
