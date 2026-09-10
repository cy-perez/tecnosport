package co.tecnosport.api.infrastructure.reversion;

import co.tecnosport.api.infrastructure.reversion.entidad.SolicitudReversionJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudReversionJpaRepository
    extends JpaRepository<SolicitudReversionJpaEntity, UUID> {

  Optional<SolicitudReversionJpaEntity> findBySolicitudId(UUID solicitudId);

  List<SolicitudReversionJpaEntity> findByPedidoIdOrderByRadicadaEnDesc(UUID pedidoId);
}
