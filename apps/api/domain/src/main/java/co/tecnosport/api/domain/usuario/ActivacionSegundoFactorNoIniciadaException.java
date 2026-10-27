package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/** Se confirmó una activación que no existe o que venció sin confirmarse. */
public final class ActivacionSegundoFactorNoIniciadaException extends ExcepcionDeDominio {

  public ActivacionSegundoFactorNoIniciadaException() {
    super("No hay una activación del segundo factor en curso. Empieza de nuevo.");
  }
}
