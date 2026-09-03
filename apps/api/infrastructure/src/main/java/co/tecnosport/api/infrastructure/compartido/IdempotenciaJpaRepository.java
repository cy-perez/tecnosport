package co.tecnosport.api.infrastructure.compartido;

import co.tecnosport.api.infrastructure.compartido.entidad.IdempotenciaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotenciaJpaRepository extends JpaRepository<IdempotenciaJpaEntity, String> {}
