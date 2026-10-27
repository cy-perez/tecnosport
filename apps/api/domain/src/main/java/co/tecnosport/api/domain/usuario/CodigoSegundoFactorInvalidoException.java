package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * El código no coincide con el de la aplicación de autenticación ni con un código de respaldo sin
 * usar. ADR-0096.
 */
public final class CodigoSegundoFactorInvalidoException extends ExcepcionDeDominio {

  public CodigoSegundoFactorInvalidoException() {
    super("El código no es válido.");
  }
}
