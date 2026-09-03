package co.tecnosport.api.application.pago;

public final class PagoNoEncontradoException extends RuntimeException {

  public PagoNoEncontradoException(String referencia) {
    super("No existe un pago con referencia " + referencia + ".");
  }
}
