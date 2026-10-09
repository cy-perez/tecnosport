package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarcaJpaRepository extends JpaRepository<MarcaJpaEntity, UUID> {

  /**
   * Una fila por marca <b>y línea</b>: la marca se repite tantas veces como líneas tenga con algo
   * publicado y disponible, y nunca aparece la que no tiene nada.
   *
   * <p>Hasta el 6 de octubre de 2026 esto era un {@code exists} correlacionado que devolvía la
   * marca a secas, porque en un {@code join} se repetía una vez por producto y lo que se quería era
   * la marca una sola vez. Con las líneas la repetición pasó a ser el resultado que se busca, así
   * que entra el {@code join} y es el {@code group by} —y no la forma de la consulta— quien deja
   * una fila por línea en vez de una por producto.
   *
   * <p>El estado llega por parámetro y no escrito en la consulta para que quede atado al enum del
   * dominio: si algún día {@code PUBLICADO} se llama de otra forma, esto falla al compilar en vez
   * de devolver cero marcas en silencio.
   *
   * <p>Con la disponibilidad también, desde el 4 de octubre de 2026: la vitrina lista lo publicado
   * <b>y</b> disponible (V71), y esta consulta solo pedía publicado. Una marca cuyos productos
   * publicados estaban todos vencidos u ocultos aparecía en el filtro y llevaba a una rejilla
   * vacía, que es el "filtro que lleva a nada" contra el que ya advierte {@code ListarCategorias}.
   *
   * <p>Los dos cruces van por identificador y no por asociación porque {@code ProductoJpaEntity}
   * guarda {@code marcaId} y {@code categoriaId} como columnas sueltas; HQL admite unir entidades
   * sin relación declarada con {@code join ... on}.
   *
   * <p>La línea se lee de la <b>categoría</b> y no del producto, que no la tiene: es el árbol quien
   * sabe de qué línea cuelga cada hoja ({@code V63}).
   */
  @Query(
      "select m.id as marcaId, m.nombre as nombre, c.linea as linea "
          + "from MarcaJpaEntity m "
          + "join ProductoJpaEntity p on p.marcaId = m.id "
          + "join CategoriaJpaEntity c on c.id = p.categoriaId "
          + "where p.estado = :estado and p.estadoDisponibilidad = :disponibilidad "
          + "group by m.id, m.nombre, c.linea "
          + "order by m.nombre")
  List<MarcaYLinea> findLineasConProductosEnEstado(
      @Param("estado") String estado, @Param("disponibilidad") String disponibilidad);

  /**
   * Una marca y una de sus líneas. Proyección de interfaz: los alias de la consulta la rellenan.
   */
  interface MarcaYLinea {
    UUID getMarcaId();

    String getNombre();

    String getLinea();
  }

  /**
   * Spring Data la traduce a {@code where lower(nombre) = lower(?)}, que es literalmente el índice
   * único de {@code V56} — así que además de responder la pregunta, la consulta lo usa.
   */
  boolean existsByNombreIgnoreCase(String nombre);

  boolean existsByNombreIgnoreCaseAndIdNot(String nombre, UUID id);

  /** Por identificador, como la consulta de arriba: el producto guarda {@code marcaId} suelto. */
  @Query("select count(p) from ProductoJpaEntity p where p.marcaId = :marcaId")
  long contarProductos(@Param("marcaId") UUID marcaId);

  /**
   * La marca con ese nombre sin distinguir mayúsculas: la que el índice de {@code V56} deja una.
   */
  Optional<MarcaJpaEntity> findFirstByNombreIgnoreCase(String nombre);
}
