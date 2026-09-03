package co.tecnosport.api.infrastructure.pago;

import co.tecnosport.api.infrastructure.pago.entidad.PagoJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoJpaRepository extends JpaRepository<PagoJpaEntity, UUID> {

  Optional<PagoJpaEntity> findByReferencia(String referencia);

  List<PagoJpaEntity> findByPedidoId(UUID pedidoId);

  List<PagoJpaEntity> findByEstadoAndIdTransaccionWompiIsNotNullAndCreadoEnBefore(
      String estado, Instant creadoEn);
}
