package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/** La operación pide un segundo factor activo y la cuenta no lo tiene. */
public final class SegundoFactorNoActivoException extends ExcepcionDeDominio {

  public SegundoFactorNoActivoException() {
    super("La cuenta no tiene el segundo factor activo.");
  }
}
