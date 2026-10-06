package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.Objects;

/** Edición completa desde el panel, activación y desactivación incluidas. */
public final class EditarProveedor {

  private final RepositorioProveedores repositorioProveedores;

  public EditarProveedor(RepositorioProveedores repositorioProveedores) {
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
  }

  public Proveedor ejecutar(EditarProveedorComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Proveedor proveedor =
        repositorioProveedores
            .buscarPorId(comando.id())
            .orElseThrow(() -> new ProveedorNoEncontradoException(comando.id()));
    proveedor.editar(
        comando.nombre(),
        comando.linea(),
        comando.telefonoWhatsApp(),
        comando.nombreEnExportacion(),
        comando.activo(),
        comando.publicacionAutomatica(),
        comando.factorDeMargen(),
        comando.ordenDePublicacion());
    repositorioProveedores.actualizar(proveedor);
    return proveedor;
  }
}
