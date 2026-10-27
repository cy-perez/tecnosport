package co.tecnosport.api.infrastructure.pedido;

import co.tecnosport.api.infrastructure.pedido.entidad.EnlaceDeCompraJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnlaceDeCompraJpaRepository extends JpaRepository<EnlaceDeCompraJpaEntity, UUID> {

  Optional<EnlaceDeCompraJpaEntity> findByToken(String token);

  /** {@code select ... for update}: ver {@code RepositorioEnlacesDeCompra.buscarParaUsar}. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select e from EnlaceDeCompraJpaEntity e where e.token = :token and e.varianteId = :variante")
  Optional<EnlaceDeCompraJpaEntity> findParaUsar(
      @Param("token") String token, @Param("variante") UUID varianteId);

  /** {@code select ... for update}: ver {@code RepositorioEnlacesDeCompra}. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from EnlaceDeCompraJpaEntity e where e.id = :id")
  Optional<EnlaceDeCompraJpaEntity> findByIdParaActualizar(@Param("id") UUID id);

  List<EnlaceDeCompraJpaEntity> findByCreadoEnGreaterThanEqualOrderByCreadoEnDesc(Instant desde);

  Optional<EnlaceDeCompraJpaEntity> findFirstByPedidoIdAndVarianteIdOrderByExpiraEnDesc(
      UUID pedidoId, UUID varianteId);
}
