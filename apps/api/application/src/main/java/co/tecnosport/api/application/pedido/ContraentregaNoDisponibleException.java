package co.tecnosport.api.application.pedido;

public final class ContraentregaNoDisponibleException extends RuntimeException {

  public ContraentregaNoDisponibleException() {
    super("Contraentrega no está disponible para este pedido.");
  }
}
