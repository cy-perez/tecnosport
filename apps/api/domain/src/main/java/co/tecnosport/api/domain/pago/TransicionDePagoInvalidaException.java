package co.tecnosport.api.domain.pago;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class TransicionDePagoInvalidaException extends ExcepcionDeDominio {

  public TransicionDePagoInvalidaException(EstadoPago actual, EstadoPago siguiente) {
    super("Un pago en estado " + actual + " no puede pasar a " + siguiente + ".");
  }
}
