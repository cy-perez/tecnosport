package co.tecnosport.api.application.retracto;

import co.tecnosport.api.domain.compartido.Dinero;

/**
 * Devolver más de lo que el comprador pagó no es un reembolso, es una pérdida. La comprobación vive
 * en aplicación y no en {@code Reembolso} porque exige el total del pedido, que es otro agregado.
 */
public class MontoDeReembolsoInvalidoException extends RuntimeException {

  public MontoDeReembolsoInvalidoException(Dinero monto, Dinero total) {
    super(
        "El reembolso de " + monto.valor() + " supera el total del pedido, " + total.valor() + ".");
  }
}
