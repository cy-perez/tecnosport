package co.tecnosport.api.infrastructure.difusion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicacionEnRedJpaRepository
    extends JpaRepository<PublicacionEnRedJpaEntity, UUID> {

  List<PublicacionEnRedJpaEntity> findByProductoIdOrderBySolicitadaEnDesc(UUID productoId);

  List<PublicacionEnRedJpaEntity> findByProductoIdAndRedOrderBySolicitadaEnDesc(
      UUID productoId, String red);

  boolean existsByProductoIdAndRedAndSolicitadaEnGreaterThanEqual(
      UUID productoId, String red, Instant desde);
}
