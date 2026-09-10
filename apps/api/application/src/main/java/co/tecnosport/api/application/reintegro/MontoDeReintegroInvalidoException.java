package co.tecnosport.api.application.reintegro;

import co.tecnosport.api.domain.compartido.Dinero;

/**
 * Devolver más de lo que el comprador pagó no es un reintegro, es una pérdida. La comprobación vive
 * en aplicación y no en {@code Reintegro} porque exige el total del pedido, que es otro agregado.
 *
 * <p>Compartida por los cinco motivos: ninguno de ellos puede devolver más de lo que entró. Esa
 * frase estaba escrita aquí desde el primer día y el código solo la cumplía a medias — comparaba el
 * monto de cada operación contra el total y nunca la suma de las anteriores. Quien la aplica de
 * verdad es {@link TopeDeReintegro}.
 *
 * <p>Dos mensajes, porque son dos hechos distintos para quien los lee en el panel: "esto solo no
 * cabe en el pedido" y "esto no cabe en lo que queda". El segundo sin la cifra ya devuelta sería
 * incomprensible: un reintegro de 500.000 rechazado en un pedido de 500.000 parece un error del
 * sistema hasta que se dice que ya se habían devuelto 500.000.
 */
public class MontoDeReintegroInvalidoException extends RuntimeException {

  public MontoDeReintegroInvalidoException(Dinero monto, Dinero total) {
    super(
        "El reintegro de " + monto.valor() + " supera el total del pedido, " + total.valor() + ".");
  }

  public MontoDeReintegroInvalidoException(Dinero monto, Dinero yaDevuelto, Dinero total) {
    super(
        "El reintegro de "
            + monto.valor()
            + " no cabe: de este pedido ya se devolvieron "
            + yaDevuelto.valor()
            + " de un total de "
            + total.valor()
            + ".");
  }
}
