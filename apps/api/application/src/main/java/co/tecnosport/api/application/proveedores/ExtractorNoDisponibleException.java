package co.tecnosport.api.application.proveedores;

/**
 * La API del modelo rechazó la petición por la cuenta y no por el mensaje: sin saldo, la clave no
 * vale, no tiene permiso, o el modelo configurado ya no existe. Ninguna publicación del lote va a
 * salir distinta mientras eso siga así, así que el lote <b>se pausa</b> y la publicación se queda
 * pendiente: al reanudarlo, después de arreglar la cuenta, sigue donde iba.
 *
 * <p>Existe por la ingesta de Meraki del 11 de octubre de 2026: un 400 tras otro marcó las 27
 * publicaciones como rotas en seis segundos, sin una línea en el registro, y el panel las contó
 * como descartadas.
 */
public final class ExtractorNoDisponibleException extends RuntimeException {

  private final int estado;

  /**
   * @param estado el código HTTP con que respondió la API
   */
  public ExtractorNoDisponibleException(int estado, String motivo) {
    super(motivo);
    this.estado = estado;
  }

  public int estado() {
    return estado;
  }
}
