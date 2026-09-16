package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.GuiaEnvioJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuiaEnvioJpaRepository extends JpaRepository<GuiaEnvioJpaEntity, UUID> {

  List<GuiaEnvioJpaEntity> findByEnvioIdOrderByNumeroAsc(UUID envioId);

  Optional<GuiaEnvioJpaEntity> findByNumero(String numero);
}
