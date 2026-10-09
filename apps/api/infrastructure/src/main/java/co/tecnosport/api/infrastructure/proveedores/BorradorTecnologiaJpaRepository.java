package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.BorradorTecnologiaJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorradorTecnologiaJpaRepository
    extends JpaRepository<BorradorTecnologiaJpaEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from BorradorTecnologiaJpaEntity b where b.id = :id")
  Optional<BorradorTecnologiaJpaEntity> buscarConBloqueo(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select b from BorradorTecnologiaJpaEntity b where b.proveedorId = :proveedorId"
          + " and b.idModelo = :idModelo and b.estado = 'EN_REVISION'")
  Optional<BorradorTecnologiaJpaEntity> buscarEnRevisionConBloqueo(
      @Param("proveedorId") UUID proveedorId, @Param("idModelo") String idModelo);

  List<BorradorTecnologiaJpaEntity> findByProveedorIdAndIdModeloAndEstadoNot(
      UUID proveedorId, String idModelo, String estado);

  List<BorradorTecnologiaJpaEntity> findByEstadoOrderByCreadoEnDesc(String estado);
}
