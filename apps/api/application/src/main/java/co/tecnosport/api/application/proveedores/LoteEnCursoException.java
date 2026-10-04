package co.tecnosport.api.application.proveedores;

/** El lote está en la cola o a medio procesar: borrarlo le quitaría el piso al hilo que lo lee. */
public final class LoteEnCursoException extends RuntimeException {

  public LoteEnCursoException() {
    super("La ingesta todavía se está procesando. Espera a que termine para eliminarla.");
  }
}
