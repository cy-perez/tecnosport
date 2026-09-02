package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class AtributoInvalidoException extends ExcepcionDeDominio {

  public AtributoInvalidoException(String mensaje) {
    super(mensaje);
  }
}
