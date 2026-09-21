package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class ImagenDeGaleriaNoEncontradaException extends ExcepcionDeDominio {

  public ImagenDeGaleriaNoEncontradaException(String mensaje) {
    super(mensaje);
  }
}
