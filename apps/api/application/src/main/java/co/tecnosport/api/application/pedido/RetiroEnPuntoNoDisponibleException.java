package co.tecnosport.api.application.pedido;

public final class RetiroEnPuntoNoDisponibleException extends RuntimeException {

  public RetiroEnPuntoNoDisponibleException() {
    super("La recogida en el punto no está disponible.");
  }
}
