package co.tecnosport.api.application.proveedores.tecnologia;

/**
 * Se intentó importar una lista de precios a un proveedor de bolsos o de ropa: su catálogo entra
 * por la exportación del chat. La simétrica de {@code ProveedorDeListasException}.
 */
public final class ProveedorSinListasException extends RuntimeException {
  public ProveedorSinListasException(String nombre) {
    super(
        "El proveedor "
            + nombre
            + " no es de tecnología: sus productos entran por la exportación del chat, no por"
            + " una lista de precios.");
  }
}
