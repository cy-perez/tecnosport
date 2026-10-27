package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.infrastructure.usuario.entidad.SegundoFactorJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SegundoFactorJpaRepository extends JpaRepository<SegundoFactorJpaEntity, UUID> {

  /**
   * {@code select ... for update}: ver {@code RepositorioSegundosFactores.buscarParaActualizar}.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from SegundoFactorJpaEntity f where f.usuarioId = :usuarioId")
  Optional<SegundoFactorJpaEntity> findParaActualizar(@Param("usuarioId") UUID usuarioId);

  boolean existsByUsuarioIdAndActivadoEnIsNotNull(UUID usuarioId);
}
