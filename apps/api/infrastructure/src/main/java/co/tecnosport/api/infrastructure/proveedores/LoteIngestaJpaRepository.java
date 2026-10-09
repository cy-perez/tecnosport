package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.LoteIngestaJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoteIngestaJpaRepository extends JpaRepository<LoteIngestaJpaEntity, UUID> {

  Page<LoteIngestaJpaEntity> findByProveedorId(UUID proveedorId, Pageable pageable);

  List<LoteIngestaJpaEntity> findByEstadoInOrderByCreadoEnAsc(Collection<String> estados);

  /**
   * {@code select ... for update}: ver {@code RepositorioLotesIngesta.buscarPorIdParaActualizar}.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select l from LoteIngestaJpaEntity l where l.id = :id")
  Optional<LoteIngestaJpaEntity> findParaActualizarById(@Param("id") UUID id);
}
