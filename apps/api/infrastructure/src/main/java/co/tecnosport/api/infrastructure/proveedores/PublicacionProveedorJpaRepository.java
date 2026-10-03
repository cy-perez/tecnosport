package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.PublicacionProveedorJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PublicacionProveedorJpaRepository
    extends JpaRepository<PublicacionProveedorJpaEntity, UUID> {

  List<PublicacionProveedorJpaEntity> findByLoteIdOrderByFechaAscCreadoEnAsc(UUID loteId);

  @Query(
      "select p.mensajePrincipalId from PublicacionProveedorJpaEntity p"
          + " where p.id <> :publicacionId and p.mensajePrincipalId in :mensajeIds")
  List<UUID> principalesDeOtras(UUID publicacionId, Collection<UUID> mensajeIds);
}
