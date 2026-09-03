package co.tecnosport.api.infrastructure.pago;

import co.tecnosport.api.infrastructure.pago.entidad.EventoPagoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoPagoJpaRepository extends JpaRepository<EventoPagoJpaEntity, UUID> {

  List<EventoPagoJpaEntity> findByPagoIdOrderByRecibidoEnAsc(UUID pagoId);

  void deleteByPagoId(UUID pagoId);
}
