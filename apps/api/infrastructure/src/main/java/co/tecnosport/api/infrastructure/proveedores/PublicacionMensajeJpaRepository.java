package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.PublicacionMensajeJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PublicacionMensajeJpaRepository
    extends JpaRepository<PublicacionMensajeJpaEntity, PublicacionMensajeJpaEntity.Clave> {

  List<PublicacionMensajeJpaEntity> findByClavePublicacionIdInOrderByClaveOrdenAsc(
      Collection<UUID> publicacionIds);

  void deleteByClavePublicacionId(UUID publicacionId);

  @Query(
      "select m.mensajeId from PublicacionMensajeJpaEntity m"
          + " where m.clave.publicacionId <> :publicacionId and m.mensajeId in :mensajeIds")
  List<UUID> usadosPorOtras(UUID publicacionId, Collection<UUID> mensajeIds);
}
