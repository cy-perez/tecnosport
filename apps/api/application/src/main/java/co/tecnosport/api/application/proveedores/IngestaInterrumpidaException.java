package co.tecnosport.api.application.proveedores;

/**
 * El hilo de la ingesta se interrumpió mientras un lote esperaba en pausa: la aplicación se está
 * apagando. El mensaje lo lee una persona en el panel, en el detalle del lote.
 */
public class IngestaInterrumpidaException extends RuntimeException {

  public IngestaInterrumpidaException() {
    super(
        "La aplicación se apagó mientras la ingesta estaba en pausa. Elimínala y vuelve a subir"
            + " la exportación para procesarla entera.");
  }
}
