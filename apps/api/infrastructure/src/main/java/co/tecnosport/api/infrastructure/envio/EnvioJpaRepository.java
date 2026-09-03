package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.EnvioJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvioJpaRepository extends JpaRepository<EnvioJpaEntity, UUID> {}
