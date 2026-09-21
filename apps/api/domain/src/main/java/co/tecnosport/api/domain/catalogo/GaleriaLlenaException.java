package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class GaleriaLlenaException extends ExcepcionDeDominio {

  public GaleriaLlenaException(String mensaje) {
    super(mensaje);
  }
}
