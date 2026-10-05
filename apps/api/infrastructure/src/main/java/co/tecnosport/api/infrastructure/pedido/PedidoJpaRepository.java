package co.tecnosport.api.infrastructure.pedido;

import co.tecnosport.api.infrastructure.pedido.entidad.PedidoJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoJpaRepository extends JpaRepository<PedidoJpaEntity, UUID> {

  /** {@code select ... for update}: ver {@code RepositorioPedidos.buscarPorIdParaModificar}. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PedidoJpaEntity p where p.id = :id")
  Optional<PedidoJpaEntity> findByIdParaModificar(@Param("id") UUID id);

  boolean existsByCorreoAndEstado(String correo, String estado);

  @Query(
      "select p.id from PedidoJpaEntity p where p.estado = :estado and p.creadoEn < :corte"
          + " order by p.creadoEn asc")
  List<UUID> findIdsByEstadoAndCreadoEnBefore(
      @Param("estado") String estado, @Param("corte") Instant corte, Pageable pagina);

  @Query(
      nativeQuery = true,
      value =
          "select exists (select 1 from pedido p join historial_pedido h on h.pedido_id = p.id"
              + " where p.correo = :correo and h.estado = 'RECHAZADO_EN_ENTREGA')")
  boolean algunaVezRechazadoPorCorreo(@Param("correo") String correo);

  /**
   * Por los últimos diez dígitos: el mismo celular llega como {@code 300 123 4567}, {@code +57
   * 3001234567} o {@code 573001234567}.
   */
  @Query(
      nativeQuery = true,
      value =
          "select exists (select 1 from pedido p join historial_pedido h on h.pedido_id = p.id"
              + " where p.telefono_contacto is not null"
              + " and right(regexp_replace(p.telefono_contacto, '[^0-9]', '', 'g'), 10)"
              + " = right(regexp_replace(:telefono, '[^0-9]', '', 'g'), 10)"
              + " and h.estado = 'RECHAZADO_EN_ENTREGA')")
  boolean algunaVezRechazadoPorTelefono(@Param("telefono") String telefono);

  Optional<PedidoJpaEntity> findByNumeroPedidoAndCorreo(String numeroPedido, String correo);

  Page<PedidoJpaEntity> findByEstado(String estado, Pageable pageable);

  List<PedidoJpaEntity> findByEstadoInAndAvisoPlazoEntregaEnviadoEnIsNullAndCreadoEnBefore(
      Collection<String> estados, Instant creadosAntesDe);

  /**
   * El {@code where ... is null} es lo que serializa a varias instancias: solo una de las que lo
   * intenten a la vez actualiza una fila, y las demás reciben cero.
   *
   * <p>Las dos banderas son obligatorias y las dos costaron una vuelta de diagnóstico, la misma que
   * apps/api/CLAUDE.md ya tenía anotada de la Fase 4. Una sentencia masiva se traduce a SQL directo
   * y no pasa por el contexto de persistencia: sin {@code flushAutomatically} no ve las escrituras
   * que siguen pendientes en memoria —y actualiza cero filas—, y sin {@code clearAutomatically}
   * quien lea después se lleva la copia vieja en caché, todavía con el aviso en nulo. Nunca una sin
   * la otra.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update PedidoJpaEntity p set p.avisoPlazoEntregaEnviadoEn = :ahora
      where p.id = :pedidoId and p.avisoPlazoEntregaEnviadoEn is null
      """)
  int reclamarAvisoDePlazo(@Param("pedidoId") UUID pedidoId, @Param("ahora") Instant ahora);

  List<PedidoJpaEntity> findByEstadoInAndComprobanteEnviadoEnIsNull(Collection<String> estados);

  /** El mismo reclamo condicional del aviso de plazo, y por las mismas dos razones. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update PedidoJpaEntity p set p.comprobanteEnviadoEn = :ahora
      where p.id = :pedidoId and p.comprobanteEnviadoEn is null
      """)
  int reclamarComprobante(@Param("pedidoId") UUID pedidoId, @Param("ahora") Instant ahora);

  /**
   * Devuelve el reclamo de un comprobante que no se pudo mandar. Ver {@code liberarComprobante}.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update PedidoJpaEntity p set p.comprobanteEnviadoEn = null where p.id = :pedidoId")
  int liberarComprobante(@Param("pedidoId") UUID pedidoId);

  /** Devuelve el reclamo de un aviso de plazo que no se pudo mandar. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update PedidoJpaEntity p set p.avisoPlazoEntregaEnviadoEn = null where p.id = :pedidoId")
  int liberarAvisoDePlazo(@Param("pedidoId") UUID pedidoId);
}
