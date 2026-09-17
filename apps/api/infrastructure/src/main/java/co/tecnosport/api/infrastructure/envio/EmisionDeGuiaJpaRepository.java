package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.EmisionDeGuiaJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmisionDeGuiaJpaRepository extends JpaRepository<EmisionDeGuiaJpaEntity, UUID> {

  Optional<EmisionDeGuiaJpaEntity> findByPedidoIdAndEstado(UUID pedidoId, String estado);

  /**
   * De la más vieja a la más nueva: si hay más de las que caben en un lote, la que lleva más rato
   * esperando es la que más urge. Y el lote está acotado porque cada envío de cada emisión es una
   * llamada al proveedor, que admite dos peticiones por segundo.
   */
  List<EmisionDeGuiaJpaEntity> findByEstadoOrderBySolicitadaEnAsc(String estado, Limit limite);
}
