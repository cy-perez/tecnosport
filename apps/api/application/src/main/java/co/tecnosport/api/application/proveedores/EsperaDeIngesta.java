package co.tecnosport.api.application.proveedores;

/**
 * Cómo espera el trabajador mientras un lote está en pausa: un rato, y vuelve a mirar.
 *
 * <p>Es un puerto y no un {@code Thread.sleep} dentro del caso de uso para que la prueba pueda
 * reanudar o detener el lote en el momento de la espera, sin dormir de verdad.
 */
public interface EsperaDeIngesta {

  /**
   * @throws IngestaInterrumpidaException si el hilo se interrumpe mientras espera —la aplicación se
   *     está apagando—
   */
  void esperar();
}
