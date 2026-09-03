package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.infrastructure.usuario.entidad.SesionRefrescoJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SesionRefrescoJpaRepository extends JpaRepository<SesionRefrescoJpaEntity, UUID> {

  /**
   * Revoca de una sola sentencia toda una familia (docs/08-seguridad-legal.md: detección de
   * reutilización) — más simple que leer cada fila, llamar {@code SesionRefresco.revocar} y volver
   * a guardarlas. Como todo {@code @Modifying}, exige una transacción activa de quien llame (mismo
   * criterio que los métodos con {@code @Lock} de {@code RepositorioInventarioJpa}).
   */
  // clearAutomatically: un update en bloque no sincroniza el contexto de persistencia — sin esto,
  // un buscarPorId inmediatamente después devolvería la entidad cacheada de antes del update, no
  // la fila real ya revocada.
  @Modifying(clearAutomatically = true)
  @Query(
      "update SesionRefrescoJpaEntity s set s.revocadoEn = :ahora "
          + "where s.familiaId = :familiaId and s.revocadoEn is null")
  void revocarFamilia(@Param("familiaId") UUID familiaId, @Param("ahora") Instant ahora);
}
