package co.tecnosport.api.infrastructure.compartido;

import co.tecnosport.api.infrastructure.compartido.entidad.LimiteIntentosJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LimiteIntentosJpaRepository
    extends JpaRepository<LimiteIntentosJpaEntity, String> {}
