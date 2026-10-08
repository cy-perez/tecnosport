package co.tecnosport.api.application.usuario;

/** La credencial de Google no se pudo verificar: firma, audiencia, emisor o vencimiento. */
public final class CredencialGoogleInvalidaException extends RuntimeException {

  public CredencialGoogleInvalidaException() {
    super("La credencial de Google no es válida.");
  }
}
