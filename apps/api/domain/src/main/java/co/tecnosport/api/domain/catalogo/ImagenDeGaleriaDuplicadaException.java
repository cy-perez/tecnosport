package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class ImagenDeGaleriaDuplicadaException extends ExcepcionDeDominio {

  public ImagenDeGaleriaDuplicadaException(String mensaje) {
    super(mensaje);
  }
}
