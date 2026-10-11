package co.tecnosport.api.application.proveedores;

/**
 * La cola de afuera —Cloud Tasks— no aceptó el lote: no respondió, o respondió con un error. El
 * lote no se pierde por eso si ya tenía avance: la tarea periódica vuelve a encolar lo que lleva
 * demasiado en la cola sin moverse ({@link RecuperarIngestasAbandonadas}).
 */
public final class ColaDeIngestasNoDisponibleException extends RuntimeException {

  public ColaDeIngestasNoDisponibleException(Throwable causa) {
    super("No pudimos poner la ingesta en la cola. Vuelve a intentarlo en unos minutos.", causa);
  }

  public ColaDeIngestasNoDisponibleException(String detalle) {
    this(new IllegalStateException(detalle));
  }
}
