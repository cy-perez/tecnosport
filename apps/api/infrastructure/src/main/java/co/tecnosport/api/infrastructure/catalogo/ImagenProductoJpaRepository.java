package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagenProductoJpaRepository extends JpaRepository<ImagenProductoJpaEntity, UUID> {

  List<ImagenProductoJpaEntity> findByProductoIdIn(Collection<UUID> productoIds);

  /** A lo sumo una fila por producto (constraint única en BD, {@code variante_id is null}). */
  Optional<ImagenProductoJpaEntity> findByProductoIdAndTipoAndVarianteIdIsNull(
      UUID productoId, String tipo);

  /**
   * Por id <b>y</b> producto: un id de imagen suelto no puede borrar la foto de otro producto, ni
   * aunque el caso de uso se equivoque. Devuelve cuántas filas borró, que es lo que permite
   * distinguir "no era de este producto" de "ya no estaba".
   */
  int deleteByIdAndProductoId(UUID id, UUID productoId);

  List<ImagenProductoJpaEntity> findBySetRotacionId(UUID setRotacionId);

  void deleteBySetRotacionId(UUID setRotacionId);
}
