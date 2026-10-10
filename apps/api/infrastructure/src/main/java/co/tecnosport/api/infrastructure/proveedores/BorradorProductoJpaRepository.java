package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.BorradorProductoJpaEntity;
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

  @Query(
      "select b.id from BorradorProductoJpaEntity b where b.estado in :estados"
          + " order by b.creadoEn asc, b.id asc")
  List<UUID> idsEnEstados(@Param("estados") Collection<String> estados, Pageable pagina);

  long countByEstadoIn(Collection<String> estados);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from BorradorProductoJpaEntity b where b.id = :id")
  Optional<BorradorProductoJpaEntity> buscarConBloqueo(@Param("id") UUID id);

  boolean existsByProveedorIdAndEstadoAndHuella(UUID proveedorId, String estado, String huella);

  /**
   * Los borradores en revisión del proveedor, vistos como anuncios: el texto del mensaje principal
   * de su publicación —el cuerpo o, si no tiene, el pie de foto—, el pHash de la principal que
   * guardó el borrador y los de todas las fotos de la publicación que lo tienen, separados por
   * coma. Una sola consulta por publicación del lote, sin cargar filas enteras con su JSON crudo.
   */
  @Query(
      value =
          """
          select b.id as borradorId,
                 coalesce(m.texto, m.pie_de_foto) as texto,
                 b.phash as phashPrincipal,
                 (select string_agg(mm.phash, ',')
                    from publicacion_mensaje pm
                    join mensaje_proveedor mm on mm.id = pm.mensaje_id
                   where pm.publicacion_id = p.id
                     and pm.rol = 'MEDIO'
                     and mm.phash is not null) as phashes,
                 l.chat_de_caballero as deChatDeCaballero
            from borrador_producto b
            join publicacion_proveedor p on p.id = b.publicacion_id
            join mensaje_proveedor m on m.id = p.mensaje_principal_id
            join lote_ingesta l on l.id = p.lote_id
           where b.proveedor_id = :proveedorId
             and b.estado = 'EN_REVISION'
          """,
      nativeQuery = true)
  List<AnuncioFila> anunciosEnRevision(@Param("proveedorId") UUID proveedorId);

  /** Lo que hace falta para reconocer un anuncio repetido. */
  interface AnuncioFila {
    UUID getBorradorId();

    String getTexto();

    String getPhashPrincipal();

    String getPhashes();

    boolean getDeChatDeCaballero();
  }

  /** Lo que el pHash necesita de un borrador que ya es producto. */
  interface HuellaVisualFila {
    UUID getProductoId();

    String getPhash();
  }
}
