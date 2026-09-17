package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.EnvioEnPlataformaJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvioEnPlataformaJpaRepository
    extends JpaRepository<EnvioEnPlataformaJpaEntity, EnvioEnPlataformaJpaEntity.Llave> {

  List<EnvioEnPlataformaJpaEntity> findByEmisionIdOrderByPosicionAsc(UUID emisionId);
}
