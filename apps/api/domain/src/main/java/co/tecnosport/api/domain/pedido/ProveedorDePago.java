package co.tecnosport.api.domain.pedido;

/**
 * Quién resuelve el cobro de un {@link MetodoPago} (docs/11-pagos-y-envios.md, {@code adr/0048}).
 *
 * <p>Existe porque hasta el 20 de septiembre de 2026 la pregunta se respondía con un booleano,
 * {@code seProcesaPorPasarela()}, que decía "pasarela" y significaba "Wompi": {@code
 * CrearIntentoDePago} enrutaba a Wompi todo lo que ese predicado aprobara. Con una sola pasarela la
 * imprecisión no costaba nada. Con dos, el primer pedido de Sistecrédito se habría ido a Wompi, que
 * no lo conoce.
 *
 * <p>{@code NINGUNO} no es un hueco ni un "todavía no": es una respuesta completa. La transferencia
 * manual y la contraentrega las resuelve el negocio —un comprobante que alguien concilia, un
 * mensajero que recauda— y no hay proveedor al que preguntarle por ellas.
 */
public enum ProveedorDePago {
  NINGUNO,
  WOMPI,
  SISTECREDITO
}
