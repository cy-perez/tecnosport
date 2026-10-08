package co.tecnosport.api.application.usuario;

/** El ambiente no tiene {@code GOOGLE_CLIENT_ID}: entrar con Google no está disponible. */
public final class GoogleNoHabilitadoException extends RuntimeException {

  public GoogleNoHabilitadoException() {
    super("Entrar con Google no está habilitado en este ambiente.");
  }
}
