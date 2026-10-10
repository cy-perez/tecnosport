package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.Proveedor;
import java.util.Objects;

/** Alta de un proveedor desde el panel. Las reglas las pone {@link Proveedor#crear}. */
public final class CrearProveedor {

  private final RepositorioProveedores repositorioProveedores;

  public CrearProveedor(RepositorioProveedores repositorioProveedores) {
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
  }

  public Proveedor ejecutar(CrearProveedorComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Proveedor proveedor =
        Proveedor.crear(
            comando.nombre(),
            comando.linea(),
            comando.telefonoWhatsApp(),
            comando.nombreEnExportacion(),
            comando.factorDeMargen(),
            comando.ordenDePublicacion());
    proveedor.definirDosChatsEnUnZip(comando.dosChatsEnUnZip());
    repositorioProveedores.guardar(proveedor);
    return proveedor;
  }
}
