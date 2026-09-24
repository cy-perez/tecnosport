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

  // Lista y no Optional: la columna no lleva `unique`, y un `Optional` sobre dos filas revienta con
  // IncorrectResultSizeDataAccessException, que es una excepcion de JPA saliendo de infrastructure.
  List<PagoJpaEntity> findByIdTransaccionPasarela(String idTransaccionPasarela);

  List<PagoJpaEntity> findByEstadoAndIdTransaccionPasarelaIsNotNullAndCreadoEnBefore(
      String estado, Instant creadoEn);
}
