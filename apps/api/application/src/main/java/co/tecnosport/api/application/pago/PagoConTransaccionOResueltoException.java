package co.tecnosport.api.application.pago;

/**
 * Se intentó marcar como revisado un pago que no está en la alerta: ya tiene id de transacción —la
 * conciliación se encarga— o ya no está pendiente. 409: el panel pintó una lista vieja.
 */
public final class PagoConTransaccionOResueltoException extends RuntimeException {

  public PagoConTransaccionOResueltoException() {
    super("El pago ya tiene su transacción o ya se resolvió: no hace falta revisarlo a mano.");
  }
}
