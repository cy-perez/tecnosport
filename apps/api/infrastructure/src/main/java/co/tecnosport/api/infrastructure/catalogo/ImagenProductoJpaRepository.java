package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagenProductoJpaRepository extends JpaRepository<ImagenProductoJpaEntity, UUID> {

  List<ImagenProductoJpaEntity> findByProductoIdIn(Collection<UUID> productoIds);

  /**
   * A lo sumo una fila por producto, lo garantice o no esta consulta: el índice único de {@code
   * imagen_producto} es {@code (producto_id) where tipo = 'PRINCIPAL'} desde {@code V87}.
   *
   * <p><b>Sin {@code AndVarianteIdIsNull}, y ese filtro no sobraba: estorbaba.</b> La principal
   * puede llevar color desde el 7 de octubre de 2026 —es lo que distingue la que vale para todos
   * los tonos de la que retrata uno—, y buscarla exigiendo variante nula devolvía vacío justamente
   * para las que sí lo llevan: el producto cargaba sin imagen principal.
   */
  Optional<ImagenProductoJpaEntity> findByProductoIdAndTipo(UUID productoId, String tipo);

  /**
   * Por id <b>y</b> producto: un id de imagen suelto no puede borrar la foto de otro producto, ni
   * aunque el caso de uso se equivoque. Devuelve cuántas filas borró, que es lo que permite
   * distinguir "no era de este producto" de "ya no estaba".
   */
  int deleteByIdAndProductoId(UUID id, UUID productoId);

  List<ImagenProductoJpaEntity> findBySetRotacionId(UUID setRotacionId);

  void deleteBySetRotacionId(UUID setRotacionId);
}
