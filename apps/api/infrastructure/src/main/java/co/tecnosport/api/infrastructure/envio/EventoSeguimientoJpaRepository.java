package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.EventoSeguimientoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoSeguimientoJpaRepository
    extends JpaRepository<EventoSeguimientoJpaEntity, UUID> {

  List<EventoSeguimientoJpaEntity> findByGuiaIdOrderByOcurrioEnAsc(UUID guiaId);
}
