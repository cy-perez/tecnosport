package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/** La cuenta ya tiene el segundo factor activo: para cambiarlo, primero se desactiva. */
public final class SegundoFactorYaActivoException extends ExcepcionDeDominio {

  public SegundoFactorYaActivoException() {
    super("El segundo factor ya está activo.");
  }
}
