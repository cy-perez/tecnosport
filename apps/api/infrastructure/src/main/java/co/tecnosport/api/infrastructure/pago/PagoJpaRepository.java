package co.tecnosport.api.infrastructure.pago;

import co.tecnosport.api.infrastructure.pago.entidad.PagoJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoJpaRepository extends JpaRepository<PagoJpaEntity, UUID> {

  Optional<PagoJpaEntity> findByReferencia(String referencia);

  /**
   * {@code select ... for update}: ver {@code RepositorioPagos.buscarPorReferenciaParaModificar}.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PagoJpaEntity p where p.referencia = :referencia")
  Optional<PagoJpaEntity> findByReferenciaParaModificar(@Param("referencia") String referencia);

  List<PagoJpaEntity> findByPedidoId(UUID pedidoId);

  // Lista y no Optional: la columna no lleva `unique`, y un `Optional` sobre dos filas revienta con
  // IncorrectResultSizeDataAccessException, que es una excepcion de JPA saliendo de infrastructure.
  List<PagoJpaEntity> findByIdTransaccionPasarela(String idTransaccionPasarela);

  List<PagoJpaEntity> findBySinPedidoQueLoEspereDesdeIsNotNullOrderBySinPedidoQueLoEspereDesdeAsc();

  List<PagoJpaEntity> findByEstadoAndIdTransaccionPasarelaIsNotNullAndCreadoEnBefore(
      String estado, Instant creadoEn);
}
