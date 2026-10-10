package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Los borradores: lo que espera revisión y la constancia de lo que se renovó. */
public interface RepositorioBorradores {

  void guardar(BorradorProducto borrador);

  void actualizar(BorradorProducto borrador);

  Optional<BorradorProducto> buscarPorId(UUID id);

  /**
   * El mismo borrador, con la fila bloqueada hasta que termine la transacción ({@code FOR UPDATE}).
   * Lo usa todo caso de uso que lo cambia: {@link #actualizar} reescribe la fila entera, y sin el
   * bloqueo una confirmación de foto que leyó el borrador en revisión pisaba la aprobación que
   * terminó mientras tanto —el producto publicado y el borrador otra vez en revisión—. Con él, el
   * segundo espera y lee lo que dejó el primero. Exige una transacción abierta.
   */
  Optional<BorradorProducto> buscarPorIdParaActualizar(UUID id);

  /** Del más reciente al más antiguo. Los dos filtros son opcionales. */
  BorradoresPaginados listar(EstadoBorrador estado, UUID proveedorId, int pagina, int tamanoPagina);

  /**
   * Las huellas visuales de los productos que ya existen de este proveedor: las de los borradores
   * que terminaron en un producto —aprobados o renovaciones— y que tenían foto. Es contra lo que se
   * compara la foto de un anuncio nuevo para reconocer un producto reescrito.
   */
  List<HuellaVisual> huellasVisualesDelProveedor(UUID proveedorId);

  /**
   * ¿Ya hay un borrador de este proveedor esperando revisión con esta misma huella? Es el mismo
   * anuncio repetido antes de que alguien lo apruebe: no se abre otro.
   */
  boolean existeEnRevisionConHuella(UUID proveedorId, HuellaProveedor huella);

  /**
   * Los borradores de este proveedor que esperan revisión, vistos como anuncios: el texto del
   * proveedor y los pHash de sus fotos. Con eso se decide si un anuncio sin código es el mismo de
   * otra vez —el mismo texto y una foto en común— o la misma plantilla con otra prenda.
   */
  List<AnuncioEnRevision> anunciosEnRevision(UUID proveedorId);

  /**
   * Los ids de los borradores en alguno de estos estados, del más antiguo al más reciente, como
   * mucho {@code limite}. Es la lista de una tanda del borrado en bloque.
   */
  List<UUID> idsEnEstados(Set<EstadoBorrador> estados, int limite);

  /** Cuántos borradores hay en alguno de estos estados. */
  long contarEnEstados(Set<EstadoBorrador> estados);

  /** Cuántos borradores salieron de esta publicación, en cualquier estado. */
  long contarDePublicacion(UUID publicacionId);

  void eliminar(UUID id);
}
