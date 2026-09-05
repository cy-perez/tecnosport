package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * Credenciales correctas, pero la cuenta todavía no verificó su correo (obligatorio antes de
 * iniciar sesión, docs/08-seguridad-legal.md). A diferencia de la excepción de credenciales
 * inválidas, esta sí se puede revelar tal cual: no es información sensible, es accionable — "revisa
 * tu correo".
 */
public final class CorreoSinVerificarException extends ExcepcionDeDominio {

  public CorreoSinVerificarException() {
    super("Este correo todavía no fue verificado.");
  }
}
