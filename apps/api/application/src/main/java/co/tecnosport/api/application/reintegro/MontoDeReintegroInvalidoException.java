package co.tecnosport.api.application.reintegro;

import co.tecnosport.api.domain.compartido.Dinero;

/**
 * Devolver más de lo que el comprador pagó no es un reintegro, es una pérdida. La comprobación vive
 * en aplicación y no en {@code Reintegro} porque exige el total del pedido, que es otro agregado.
 *
 * <p>Compartida por los cinco motivos: ninguno de ellos puede devolver más de lo que entró.
 */
public class MontoDeReintegroInvalidoException extends RuntimeException {

  public MontoDeReintegroInvalidoException(Dinero monto, Dinero total) {
    super(
        "El reintegro de " + monto.valor() + " supera el total del pedido, " + total.valor() + ".");
  }
}
