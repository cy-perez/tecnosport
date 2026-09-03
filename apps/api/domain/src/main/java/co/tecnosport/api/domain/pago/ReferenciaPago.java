package co.tecnosport.api.domain.pago;

/**
 * Referencia única e idempotente del intento de pago (docs/11-pagos-y-envios.md). La firma de
 * integridad que Wompi exige al crear la transacción se calcula sobre esta referencia, el monto y
 * la moneda; el webhook la trae de vuelta para encontrar el {@link Pago} correspondiente.
 */
public record ReferenciaPago(String valor) {

  public ReferenciaPago {
    if (valor == null || valor.isBlank()) {
      throw new ReferenciaPagoInvalidaException("La referencia de pago no puede estar vacía.");
    }
    valor = valor.trim();
  }
}
