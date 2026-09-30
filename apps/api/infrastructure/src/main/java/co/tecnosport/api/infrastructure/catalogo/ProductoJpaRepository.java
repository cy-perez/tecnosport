package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoJpaRepository extends JpaRepository<ProductoJpaEntity, UUID> {

  Optional<ProductoJpaEntity> findBySlug(String slug);

  Optional<ProductoJpaEntity> findByProveedorIdAndHuellaProveedor(UUID proveedorId, String huella);

  List<ProductoJpaEntity> findByOrigenAndEstadoDisponibilidadAndVistoPorUltimaVezBefore(
      String origen, String estadoDisponibilidad, Instant limite);
}
