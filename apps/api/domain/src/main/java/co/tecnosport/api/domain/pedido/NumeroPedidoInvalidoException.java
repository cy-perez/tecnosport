package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class NumeroPedidoInvalidoException extends ExcepcionDeDominio {

  public NumeroPedidoInvalidoException(String mensaje) {
    super(mensaje);
  }
}
