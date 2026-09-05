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

  /**
   * Mismo criterio que {@link #revocarFamilia}, pero por usuario — la usa la recuperación de
   * contraseña para no dejar viva una sesión en un dispositivo ajeno.
   *
   * <p>{@code flushAutomatically = true}, a diferencia de {@link #revocarFamilia}: aquí sí importa.
   * {@code ConfirmarRecuperacion} guarda el token consumido y la clave nueva del usuario *antes* de
   * llamar este método, en la misma transacción — sin volcar esos cambios primero, el {@code
   * clearAutomatically} de abajo limpia el contexto de persistencia y los descarta en silencio (sin
   * ninguna excepción: la transacción igual confirma, solo que sin esas dos escrituras). Encontrado
   * a mano contra {@code bootRun} real, no lo atrapó ninguna prueba porque los dobles de prueba no
   * reproducen el comportamiento de flush/clear de Hibernate.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update SesionRefrescoJpaEntity s set s.revocadoEn = :ahora "
          + "where s.usuarioId = :usuarioId and s.revocadoEn is null")
  void revocarTodasDeUsuario(@Param("usuarioId") UUID usuarioId, @Param("ahora") Instant ahora);
}
