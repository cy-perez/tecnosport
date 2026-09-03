package co.tecnosport.api.domain.pago;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class ReferenciaPagoInvalidaException extends ExcepcionDeDominio {

  public ReferenciaPagoInvalidaException(String mensaje) {
    super(mensaje);
  }
}
