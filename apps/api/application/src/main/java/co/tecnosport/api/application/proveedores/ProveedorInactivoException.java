package co.tecnosport.api.application.proveedores;

/**
 * El proveedor existe pero está desactivado: no se le reciben lotes. Se desactiva, no se borra,
 * porque sus productos y sus mensajes siguen ahí y tienen que poder explicarse.
 */
public final class ProveedorInactivoException extends RuntimeException {

  public ProveedorInactivoException(String nombre) {
    super("El proveedor " + nombre + " está inactivo y no recibe ingestas.");
  }
}
