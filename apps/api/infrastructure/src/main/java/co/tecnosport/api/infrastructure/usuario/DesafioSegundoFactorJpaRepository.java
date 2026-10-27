package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.infrastructure.usuario.entidad.DesafioSegundoFactorJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DesafioSegundoFactorJpaRepository
    extends JpaRepository<DesafioSegundoFactorJpaEntity, String> {

  /** {@code select ... for update}: ver {@code RepositorioDesafiosSegundoFactor}. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from DesafioSegundoFactorJpaEntity d where d.huella = :huella")
  Optional<DesafioSegundoFactorJpaEntity> findParaConsumir(@Param("huella") String huella);

  /** Ver {@code RepositorioDesafiosSegundoFactor#purgarVencidos}. */
  @Modifying
  @Query(nativeQuery = true, value = "delete from desafio_segundo_factor where expira_en < :ahora")
  int borrarVencidos(@Param("ahora") Instant ahora);
}
