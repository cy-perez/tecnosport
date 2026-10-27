package co.tecnosport.api.application.pedido;

/** El pago en el punto solo vale para recoger en el punto (ADR-0094). */
public final class PagoEnPuntoNoDisponibleException extends RuntimeException {

  public PagoEnPuntoNoDisponibleException() {
    super("El pago en el punto solo está disponible para recoger el pedido en el punto.");
  }
}
