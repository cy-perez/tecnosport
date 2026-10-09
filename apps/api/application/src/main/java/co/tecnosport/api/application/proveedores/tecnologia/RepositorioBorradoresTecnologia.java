package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Los borradores de tecnología: un modelo de la lista esperando a que alguien lo revise. */
public interface RepositorioBorradoresTecnologia {

  void guardar(BorradorTecnologia borrador);

  void actualizar(BorradorTecnologia borrador);

  Optional<BorradorTecnologia> buscarPorId(UUID id);

  /**
   * Con bloqueo pesimista: aprobar crea variantes y libros de inventario, y dos aprobaciones del
   * mismo borrador a la vez crearían el producto dos veces.
   */
  Optional<BorradorTecnologia> buscarPorIdParaActualizar(UUID id);

  /** El que está en revisión para ese modelo de ese proveedor. Hay uno como mucho. */
  Optional<BorradorTecnologia> buscarEnRevision(UUID proveedorId, String idModelo);

  /** Los aprobados y los rechazados de ese modelo: lo que una persona ya decidió. */
  List<BorradorTecnologia> listarResueltos(UUID proveedorId, String idModelo);

  /** Del más reciente al más viejo. */
  List<BorradorTecnologia> listarPorEstado(EstadoBorrador estado);
}
