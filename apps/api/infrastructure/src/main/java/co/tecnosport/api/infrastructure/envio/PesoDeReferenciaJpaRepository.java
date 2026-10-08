package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.PesoDeReferenciaJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PesoDeReferenciaJpaRepository
    extends JpaRepository<PesoDeReferenciaJpaEntity, UUID> {}
