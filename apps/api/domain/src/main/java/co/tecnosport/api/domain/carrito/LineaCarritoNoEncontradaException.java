package co.tecnosport.api.domain.carrito;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;

public final class LineaCarritoNoEncontradaException extends ExcepcionDeDominio {

  public LineaCarritoNoEncontradaException(UUID idLinea) {
    super("No existe una línea con id " + idLinea + " en este carrito.");
  }
}
