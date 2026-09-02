package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class ProductoSinImagenPrincipalException extends ExcepcionDeDominio {

  public ProductoSinImagenPrincipalException(String mensaje) {
    super(mensaje);
  }
}
