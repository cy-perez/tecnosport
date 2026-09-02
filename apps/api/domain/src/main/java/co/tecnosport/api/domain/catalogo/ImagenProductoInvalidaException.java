package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class ImagenProductoInvalidaException extends ExcepcionDeDominio {

  public ImagenProductoInvalidaException(String mensaje) {
    super(mensaje);
  }
}
