package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.MensajeProveedorJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MensajeProveedorJpaRepository
    extends JpaRepository<MensajeProveedorJpaEntity, UUID> {

  @Query(
      "select m.idExterno from MensajeProveedorJpaEntity m"
          + " where m.proveedorId = :proveedorId and m.idExterno in :candidatos")
  List<String> idsExternosExistentes(UUID proveedorId, Collection<String> candidatos);

  List<MensajeProveedorJpaEntity> findByLoteIdOrderByEnviadoEnAscCreadoEnAscPosicionAsc(
      UUID loteId);
}
