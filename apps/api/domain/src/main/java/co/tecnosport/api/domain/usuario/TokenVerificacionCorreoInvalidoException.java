package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * El token de verificación no existe, ya se usó, o venció — un solo mensaje para los tres casos.
 */
public final class TokenVerificacionCorreoInvalidoException extends ExcepcionDeDominio {

  public TokenVerificacionCorreoInvalidoException() {
    super("El enlace de verificación no es válido o ya venció.");
  }
}
