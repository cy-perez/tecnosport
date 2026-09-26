package co.tecnosport.api.infrastructure.sugerencia;

import co.tecnosport.api.infrastructure.sugerencia.entidad.SugerenciaJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SugerenciaJpaRepository extends JpaRepository<SugerenciaJpaEntity, UUID> {}
