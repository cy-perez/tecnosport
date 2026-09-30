package co.tecnosport.api.application.proveedores;

/** La foto del proveedor no se pudo abrir como imagen. Se descarta esa foto, no el borrador. */
public final class ImagenDeProveedorIlegibleException extends RuntimeException {

  public ImagenDeProveedorIlegibleException(String referencia) {
    super("La foto " + referencia + " no se pudo abrir como imagen.");
  }
}
