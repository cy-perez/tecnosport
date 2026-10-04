package co.tecnosport.api.application.proveedores;

/**
 * Tiene un lote en la cola o a medio procesar: eliminarlo le quitaría el piso al hilo que lo lee.
 */
public final class ProveedorConIngestaEnCursoException extends RuntimeException {

  public ProveedorConIngestaEnCursoException() {
    super("El proveedor tiene una ingesta en curso. Espera a que termine para eliminarlo.");
  }
}
