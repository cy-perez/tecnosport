package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.PublicacionProveedorJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicacionProveedorJpaRepository
    extends JpaRepository<PublicacionProveedorJpaEntity, UUID> {

  List<PublicacionProveedorJpaEntity> findByLoteIdOrderByFechaAscCreadoEnAsc(UUID loteId);
}
