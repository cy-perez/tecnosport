package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * El segundo paso del inicio de sesión no existe, venció, ya se usó o agotó sus intentos: un solo
 * mensaje para los cuatro, y la salida es la misma, volver a escribir la clave.
 */
public final class DesafioSegundoFactorInvalidoException extends ExcepcionDeDominio {

  public DesafioSegundoFactorInvalidoException() {
    super("El inicio de sesión venció. Vuelve a escribir tu clave.");
  }
}
