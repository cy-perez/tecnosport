package co.tecnosport.api.application.proveedores;

/**
 * Un producto no se publica sin imagen principal, y este borrador no trae ninguna foto con archivo.
 */
public final class BorradorSinFotosException extends RuntimeException {

  public BorradorSinFotosException() {
    super(
        "El borrador no tiene fotos con archivo y un producto no se publica sin imagen principal."
            + " Sube la exportación con los archivos, o crea el producto a mano.");
  }
}
