package co.tecnosport.api.application.proveedores;

/**
 * El proveedor es de tecnología: manda listas de precios, que procesa la skill
 * `listas-de-proveedor` y se importan como borradores de tecnología. Su chat no se sube como
 * exportación, porque el extractor y la huella visual son de prendas (08/10/2026).
 */
public final class ProveedorDeListasException extends RuntimeException {
  public ProveedorDeListasException(String nombre) {
    super(
        "El proveedor "
            + nombre
            + " es de tecnología: sus productos entran por la lista de precios, no por la"
            + " exportación del chat.");
  }
}
