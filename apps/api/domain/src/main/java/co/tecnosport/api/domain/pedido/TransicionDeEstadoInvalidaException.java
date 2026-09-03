package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class TransicionDeEstadoInvalidaException extends ExcepcionDeDominio {

  public TransicionDeEstadoInvalidaException(EstadoPedido actual, EstadoPedido siguiente) {
    super("Un pedido en estado " + actual + " no puede pasar a " + siguiente + ".");
  }
}
