package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/** Un color que choca con los del producto: ya se vende en él, o ya tiene y no se puede pintar. */
public final class ProductoConColorException extends ExcepcionDeDominio {

  public ProductoConColorException(String mensaje) {
    super(mensaje);
  }
}
