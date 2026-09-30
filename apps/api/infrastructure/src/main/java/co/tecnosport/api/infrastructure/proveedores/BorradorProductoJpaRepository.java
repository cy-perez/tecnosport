package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.BorradorProductoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorradorProductoJpaRepository
    extends JpaRepository<BorradorProductoJpaEntity, UUID> {

  Page<BorradorProductoJpaEntity> findByEstado(String estado, Pageable pageable);

  Page<BorradorProductoJpaEntity> findByProveedorId(UUID proveedorId, Pageable pageable);

  Page<BorradorProductoJpaEntity> findByEstadoAndProveedorId(
      String estado, UUID proveedorId, Pageable pageable);

  /**
   * Solo las dos columnas, no la fila entera: la lista crece con cada renovación y cada fila trae
   * el JSON crudo de la extracción. Proyección de interfaz de Spring Data.
   */
  List<HuellaVisualFila> findByProveedorIdAndProductoIdIsNotNullAndPhashIsNotNull(UUID proveedorId);

  boolean existsByProveedorIdAndEstadoAndHuella(UUID proveedorId, String estado, String huella);

  /** Lo que el pHash necesita de un borrador que ya es producto. */
  interface HuellaVisualFila {
    UUID getProductoId();

    String getPhash();
  }
}
