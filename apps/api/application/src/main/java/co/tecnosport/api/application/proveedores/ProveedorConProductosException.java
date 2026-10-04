package co.tecnosport.api.application.proveedores;

/**
 * Hay productos del catálogo que salieron de este proveedor. Se desactiva en vez de eliminarse: el
 * producto guarda de dónde vino y con qué huella lo reconoce la ingesta.
 */
public final class ProveedorConProductosException extends RuntimeException {

  private final long productos;

  public ProveedorConProductosException(long productos) {
    super(
        "El proveedor tiene "
            + productos
            + " producto(s) en el catálogo: desactívalo en vez de eliminarlo.");
    this.productos = productos;
  }

  public long productos() {
    return productos;
  }
}
