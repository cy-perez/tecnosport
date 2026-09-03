package co.tecnosport.api.application.usuario;

/**
 * Mismo mensaje si el correo no existe o si la clave es incorrecta: no se filtra cuál de los dos
 * fue (docs/08-seguridad-legal.md, OWASP).
 */
public final class CredencialesInvalidasException extends RuntimeException {

  public CredencialesInvalidasException() {
    super("Correo o clave incorrectos.");
  }
}
