package co.tecnosport.api.application.usuario;

/**
 * Una sesión de refresco ya usada (o revocada) volvió a presentarse: señal de robo del token —
 * {@code RefrescarToken} ya revocó toda la familia antes de lanzar esta excepción.
 */
public final class SesionDeRefrescoComprometidaException extends RuntimeException {

  public SesionDeRefrescoComprometidaException() {
    super("La sesión de refresco ya había sido usada: se revocó toda la familia.");
  }
}
