package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class SetRotacionIncompletoException extends ExcepcionDeDominio {

  public SetRotacionIncompletoException(String mensaje) {
    super(mensaje);
  }
}
