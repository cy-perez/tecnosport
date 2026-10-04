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

  /** Lo que cuelga del proveedor: sus productos, si tiene una ingesta abierta y sus archivos. */
  DependenciasDeProveedor dependenciasDe(UUID id);

  /**
   * Borra el proveedor y su historial de ingesta: borradores, publicaciones, mensajes y lotes. Los
   * productos no: quien llama ya comprobó que no tiene.
   */
  void eliminarConSuHistorial(UUID id);
}
