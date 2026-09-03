package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.CoberturaContraentregaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoberturaContraentregaJpaRepository
    extends JpaRepository<CoberturaContraentregaJpaEntity, String> {}
