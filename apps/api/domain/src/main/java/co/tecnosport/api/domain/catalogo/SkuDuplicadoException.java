package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class SkuDuplicadoException extends ExcepcionDeDominio {

  public SkuDuplicadoException(String mensaje) {
    super(mensaje);
  }
}
