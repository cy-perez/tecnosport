package co.tecnosport.api.application.proveedores;

/**
 * El lote está en la cola, a medio procesar, en pausa o deteniéndose: borrarlo le quitaría el piso
 * al hilo que lo lee.
 */
public final class LoteEnCursoException extends RuntimeException {

  public LoteEnCursoException() {
    super(
        "La ingesta todavía está en curso o en pausa. Detenla, o espera a que termine, para"
            + " eliminarla.");
  }
}
