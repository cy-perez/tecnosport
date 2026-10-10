package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoJpaRepository extends JpaRepository<ProductoJpaEntity, UUID> {

  Optional<ProductoJpaEntity> findBySlug(String slug);

  Optional<ProductoJpaEntity> findByProveedorIdAndHuellaProveedor(UUID proveedorId, String huella);

  List<ProductoJpaEntity> findByOrigenAndEstadoDisponibilidadAndVistoPorUltimaVezBefore(
      String origen, String estadoDisponibilidad, Instant limite);

  long countByEstado(String estado);

  @Query("select p.id from ProductoJpaEntity p where p.estado = :estado order by p.id desc")
  List<UUID> idsEnEstadoAlReves(@Param("estado") String estado, Pageable pagina);

  @Query(
      "select p.id from ProductoJpaEntity p where p.estado = :estado and p.id <= :hasta"
          + " order by p.id")
  List<UUID> idsEnEstadoHasta(
      @Param("estado") String estado, @Param("hasta") UUID hasta, Pageable pagina);

  @Query(
      "select p.id from ProductoJpaEntity p where p.estado = :estado and p.id > :despuesDe"
          + " and p.id <= :hasta order by p.id")
  List<UUID> idsEnEstadoEntre(
      @Param("estado") String estado,
      @Param("despuesDe") UUID despuesDe,
      @Param("hasta") UUID hasta,
      Pageable pagina);
}
