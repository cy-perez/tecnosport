package co.tecnosport.api.application.pago;

/**
 * La pasarela no contestó, o contestó algo que no se entiende. No es un rechazo: un rechazo llega
 * con cuerpo, con código y con descripción, y eso se le puede explicar al comprador.
 *
 * <p>Se distingue a propósito porque las dos cosas se cuentan distinto. "Sistecrédito no aprobó el
 * crédito" es una respuesta; "no pudimos hablar con Sistecrédito" es una caída, y ofrecerle al
 * comprador otro medio de pago solo tiene sentido en el segundo caso.
 */
public class SistecreditoNoRespondeException extends RuntimeException {

  public SistecreditoNoRespondeException(String mensaje) {
    super(mensaje);
  }

  public SistecreditoNoRespondeException(String mensaje, Throwable causa) {
    super(mensaje, causa);
  }
}
