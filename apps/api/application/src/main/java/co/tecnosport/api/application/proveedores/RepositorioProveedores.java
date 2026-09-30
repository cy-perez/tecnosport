package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Los proveedores que mandan su surtido por WhatsApp. */
public interface RepositorioProveedores {

  void guardar(Proveedor proveedor);

  void actualizar(Proveedor proveedor);

  Optional<Proveedor> buscarPorId(UUID id);

  /** Todos, activos o no, por nombre. Son pocos: el panel los lista sin paginar. */
  List<Proveedor> listar();
}
