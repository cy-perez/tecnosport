package co.tecnosport.api.application.proveedores;

/** Sin precio del proveedor no hay huella, y sin huella el producto no se puede renovar después. */
public final class BorradorSinPrecioException extends RuntimeException {

  public BorradorSinPrecioException() {
    super(
        "El borrador no tiene precio del proveedor. Sin él no hay huella con la que reconocer el"
            + " producto cuando vuelva a aparecer; corrígelo antes de aprobar.");
  }
}
