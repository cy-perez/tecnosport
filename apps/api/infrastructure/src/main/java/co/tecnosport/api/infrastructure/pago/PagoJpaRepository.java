package co.tecnosport.api.infrastructure.pago;

import co.tecnosport.api.infrastructure.pago.entidad.PagoJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

  /** Ver {@code RepositorioPagos.buscarPendientesSinTransaccionSinRevisar}. */
  @Query(
      value =
          "select p.* from pago p where p.estado = 'PENDIENTE'"
              + " and p.id_transaccion_pasarela is null and p.creado_en < :corte"
              + " and not exists (select 1 from revision_pago_sin_transaccion r"
              + " where r.pago_id = p.id)"
              + " order by p.creado_en asc",
      nativeQuery = true)
  List<PagoJpaEntity> buscarPendientesSinTransaccionSinRevisar(@Param("corte") Instant corte);

  /** Una sola escritura condicional: dos administradores a la vez dejan una sola marca. */
  @Modifying
  @Query(
      value =
          "insert into revision_pago_sin_transaccion (pago_id, revisado_por, revisado_en)"
              + " values (:pagoId, :actor, :ahora) on conflict (pago_id) do nothing",
      nativeQuery = true)
  int marcarRevisadoSinTransaccion(
      @Param("pagoId") UUID pagoId, @Param("actor") String actor, @Param("ahora") Instant ahora);
}
