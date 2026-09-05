package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * El token de recuperación no existe, ya se usó, o venció — un solo mensaje para los tres casos.
 */
public final class TokenRecuperacionClaveInvalidoException extends ExcepcionDeDominio {

  public TokenRecuperacionClaveInvalidoException() {
    super("El enlace de recuperación no es válido o ya venció.");
  }
}
