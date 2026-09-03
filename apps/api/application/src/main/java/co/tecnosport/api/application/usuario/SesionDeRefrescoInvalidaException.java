package co.tecnosport.api.application.usuario;

/** Sesión inexistente o vencida sin usar: nada sospechoso, solo pide iniciar sesión de nuevo. */
public final class SesionDeRefrescoInvalidaException extends RuntimeException {

  public SesionDeRefrescoInvalidaException() {
    super("La sesión de refresco no es válida o ya venció.");
  }
}
