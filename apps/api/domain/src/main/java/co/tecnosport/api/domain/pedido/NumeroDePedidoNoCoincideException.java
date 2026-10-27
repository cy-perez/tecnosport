package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/**
 * El número que mostró quien vino a recoger no es el de este pedido. No se entrega: el número es lo
 * que el negocio acordó pedir para saber que la mercancía va a quien la compró (ADR-0094).
 */
public final class NumeroDePedidoNoCoincideException extends ExcepcionDeDominio {

  public NumeroDePedidoNoCoincideException() {
    super("El número de pedido presentado no coincide con el de este pedido.");
  }
}
