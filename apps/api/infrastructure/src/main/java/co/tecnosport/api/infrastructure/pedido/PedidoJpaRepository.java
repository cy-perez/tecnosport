package co.tecnosport.api.infrastructure.pedido;

import co.tecnosport.api.infrastructure.pedido.entidad.PedidoJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoJpaRepository extends JpaRepository<PedidoJpaEntity, UUID> {

  boolean existsByCorreoAndEstado(String correo, String estado);

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
}
