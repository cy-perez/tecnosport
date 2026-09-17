package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.EmisionDeGuiaJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmisionDeGuiaJpaRepository extends JpaRepository<EmisionDeGuiaJpaEntity, UUID> {

  /**
   * La abierta de un pedido. {@code findFirst} y no {@code findOne} a propósito: hay como mucho una
   * —lo garantiza el índice único parcial— pero una consulta que revienta con {@code
   * IncorrectResultSize} si el índice fallara sería un error peor de diagnosticar que devolver la
   * más vieja.
   */
  Optional<EmisionDeGuiaJpaEntity> findFirstByPedidoIdAndEstadoInOrderBySolicitadaEnAsc(
      UUID pedidoId, Collection<String> estados);

  List<EmisionDeGuiaJpaEntity> findByPedidoIdOrderBySolicitadaEnAsc(UUID pedidoId);

  /**
   * De la más vieja a la más nueva: si hay más de las que caben en un lote, la que lleva más rato
   * esperando es la que más urge. Y el lote está acotado porque cada envío de cada emisión es una
   * llamada al proveedor, que admite dos peticiones por segundo.
   */
  List<EmisionDeGuiaJpaEntity> findByEstadoOrderBySolicitadaEnAsc(String estado, Limit limite);

  /** Las que se pidieron y nunca registraron respuesta: el proceso murió en la mitad. */
  List<EmisionDeGuiaJpaEntity> findByEstadoAndSolicitadaEnBeforeOrderBySolicitadaEnAsc(
      String estado, Instant corte, Limit limite);

  /**
   * Las que piden ojo humano, de la más vieja a la más nueva. Mismo orden y mismo motivo que las
   * demás: ninguna se resuelve sola, así que la que lleva más tiempo esperando es la que más urge.
   */
  List<EmisionDeGuiaJpaEntity> findByEstadoInOrderBySolicitadaEnAsc(
      Collection<String> estados, Limit limite);
}
