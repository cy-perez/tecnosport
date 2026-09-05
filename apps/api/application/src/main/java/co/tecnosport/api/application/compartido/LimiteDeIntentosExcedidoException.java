package co.tecnosport.api.application.compartido;

/**
 * Se llegó al máximo de intentos por cuenta dentro de la ventana vigente
 * (docs/08-seguridad-legal.md).
 */
public final class LimiteDeIntentosExcedidoException extends RuntimeException {

  public LimiteDeIntentosExcedidoException() {
    super("Demasiados intentos. Intenta de nuevo más tarde.");
  }
}
