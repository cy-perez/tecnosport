package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.infrastructure.proveedores.entidad.PublicacionProveedorJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicacionProveedorJpaRepository
    extends JpaRepository<PublicacionProveedorJpaEntity, UUID> {

  List<PublicacionProveedorJpaEntity> findByLoteIdOrderByFechaAscCreadoEnAsc(UUID loteId);

  @Query(
      "select p.mensajePrincipalId from PublicacionProveedorJpaEntity p"
          + " where p.id <> :publicacionId and p.mensajePrincipalId in :mensajeIds")
  List<UUID> principalesDeOtras(UUID publicacionId, Collection<UUID> mensajeIds);

  /** El cuerpo o el pie del mensaje principal de cada publicación de los lotes de caballero. */
  @Query(
      value =
          """
          select coalesce(m.texto, m.pie_de_foto)
            from publicacion_proveedor p
            join lote_ingesta l on l.id = p.lote_id
            join mensaje_proveedor m on m.id = p.mensaje_principal_id
           where p.proveedor_id = :proveedorId
             and l.chat_de_caballero
             and coalesce(m.texto, m.pie_de_foto) is not null
          """,
      nativeQuery = true)
  List<String> textosDelChatDeCaballero(@Param("proveedorId") UUID proveedorId);
}
