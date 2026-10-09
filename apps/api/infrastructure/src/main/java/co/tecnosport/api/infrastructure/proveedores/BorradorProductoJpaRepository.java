package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.BorradorProductoJpaEntity;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  long countByPublicacionId(UUID publicacionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from BorradorProductoJpaEntity b where b.id = :id")
  Optional<BorradorProductoJpaEntity> buscarConBloqueo(@Param("id") UUID id);

  boolean existsByProveedorIdAndEstadoAndHuella(UUID proveedorId, String estado, String huella);

  /** El título y el pHash de los borradores en un estado y a un precio: proyección de interfaz. */
  List<AnuncioFila> findByProveedorIdAndEstadoAndPrecioProveedorAndTituloIsNotNull(
      UUID proveedorId, String estado, BigDecimal precioProveedor);

  /** Lo que hace falta para reconocer un anuncio repetido. */
  interface AnuncioFila {
    String getTitulo();

    String getPhash();
  }

  /** Lo que el pHash necesita de un borrador que ya es producto. */
  interface HuellaVisualFila {
    UUID getProductoId();

    String getPhash();
  }
}
