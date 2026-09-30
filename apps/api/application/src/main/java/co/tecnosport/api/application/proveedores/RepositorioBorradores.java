package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Los borradores: lo que espera revisión y la constancia de lo que se renovó. */
public interface RepositorioBorradores {

  void guardar(BorradorProducto borrador);

  void actualizar(BorradorProducto borrador);

  Optional<BorradorProducto> buscarPorId(UUID id);

  /** Del más reciente al más antiguo. Los dos filtros son opcionales. */
  BorradoresPaginados listar(EstadoBorrador estado, UUID proveedorId, int pagina, int tamanoPagina);

  /**
   * Las huellas visuales de los productos que ya existen de este proveedor: las de los borradores
   * que terminaron en un producto —aprobados o renovaciones— y que tenían foto. Es contra lo que se
   * compara la foto de un anuncio nuevo para reconocer un producto reescrito.
   */
  List<HuellaVisual> huellasVisualesDelProveedor(UUID proveedorId);
}
