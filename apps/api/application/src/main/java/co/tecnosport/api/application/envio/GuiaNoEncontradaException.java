package co.tecnosport.api.application.envio;

public final class GuiaNoEncontradaException extends RuntimeException {

  public GuiaNoEncontradaException(String numeroGuia) {
    super("No existe ninguna guía con el número " + numeroGuia + ".");
  }
}
