package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarcaJpaRepository extends JpaRepository<MarcaJpaEntity, UUID> {

  /**
   * Un {@code exists} correlacionado y no un {@code join}: la marca se repite una vez por producto
   * en un join, y lo que se quiere es la marca una sola vez. {@code ProductoJpaEntity} guarda
   * {@code marcaId} como columna suelta y no como asociación, así que el cruce va por el
   * identificador.
   *
   * <p>El estado llega por parámetro y no escrito en la consulta para que quede atado al enum del
   * dominio: si algún día {@code PUBLICADO} se llama de otra forma, esto falla al compilar en vez
   * de devolver cero marcas en silencio.
   */
  @Query(
      "select m from MarcaJpaEntity m where exists "
          + "(select 1 from ProductoJpaEntity p where p.marcaId = m.id and p.estado = :estado) "
          + "order by m.nombre")
  List<MarcaJpaEntity> findConProductosEnEstado(@Param("estado") String estado);

  /**
   * Spring Data la traduce a {@code where lower(nombre) = lower(?)}, que es literalmente el índice
   * único de {@code V56} — así que además de responder la pregunta, la consulta lo usa.
   */
  boolean existsByNombreIgnoreCase(String nombre);
}
