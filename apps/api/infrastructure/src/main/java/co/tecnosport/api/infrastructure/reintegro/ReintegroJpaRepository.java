package co.tecnosport.api.infrastructure.reintegro;

import co.tecnosport.api.infrastructure.reintegro.entidad.ReintegroJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReintegroJpaRepository extends JpaRepository<ReintegroJpaEntity, UUID> {

  List<ReintegroJpaEntity> findByPedidoIdOrderByRegistradoEnDesc(UUID pedidoId);

  Optional<ReintegroJpaEntity> findByOrigenId(UUID origenId);
}
