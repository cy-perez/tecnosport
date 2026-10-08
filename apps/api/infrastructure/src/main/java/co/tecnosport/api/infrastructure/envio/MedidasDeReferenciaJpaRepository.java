package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.MedidasDeReferenciaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedidasDeReferenciaJpaRepository
    extends JpaRepository<MedidasDeReferenciaJpaEntity, Short> {}
