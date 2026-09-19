package co.tecnosport.api.infrastructure.correo;

import co.tecnosport.api.infrastructure.correo.entidad.CorreoPendienteJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorreoPendienteJpaRepository
    extends JpaRepository<CorreoPendienteJpaEntity, UUID> {

  /** Los que toca intentar ahora, del más viejo al más nuevo: un comprobante no se adelanta. */
  @Query(
      """
      select c from CorreoPendienteJpaEntity c
      where c.enviadoEn is null and c.intentos < :maxIntentos and c.proximoIntentoEn <= :ahora
      order by c.creadoEn asc
      """)
  List<CorreoPendienteJpaEntity> buscarEnviables(
      @Param("maxIntentos") int maxIntentos, @Param("ahora") Instant ahora, Pageable limite);

  /**
   * El reclamo condicional, con las dos banderas de {@code @Modifying} que apps/api/CLAUDE.md lleva
   * anotadas desde la Fase 4: una sentencia masiva no pasa por el contexto de persistencia, así que
   * sin {@code flushAutomatically} no ve lo pendiente en memoria y sin {@code clearAutomatically}
   * quien lea después se lleva la copia vieja. Nunca una sin la otra.
   *
   * <p>Sube {@code intentos} y empuja {@code proximoIntentoEn} en el mismo golpe: eso es lo que
   * hace que dos instancias no manden el mismo correo, y de paso que un correo cuyo proceso muera a
   * mitad del envío tenga ya su reintento programado.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update CorreoPendienteJpaEntity c
      set c.intentos = c.intentos + 1, c.proximoIntentoEn = :proximoIntento
      where c.id = :id and c.enviadoEn is null and c.proximoIntentoEn <= :ahora
      """)
  int reclamar(
      @Param("id") UUID id,
      @Param("ahora") Instant ahora,
      @Param("proximoIntento") Instant proximoIntento);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update CorreoPendienteJpaEntity c set c.enviadoEn = :ahora where c.id = :id")
  int marcarEnviado(@Param("id") UUID id, @Param("ahora") Instant ahora);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update CorreoPendienteJpaEntity c set c.ultimoError = :detalle where c.id = :id")
  int registrarFallo(@Param("id") UUID id, @Param("detalle") String detalle);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "delete from CorreoPendienteJpaEntity c where c.enviadoEn is not null and c.enviadoEn <"
          + " :limite")
  int purgarEnviados(@Param("limite") Instant limite);
}
