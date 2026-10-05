package co.tecnosport.api.application.pago;

import java.util.UUID;

/**
 * El pago no está marcado como sin pedido: es el dinero de una venta, y se devuelve por los caminos
 * de esa venta —retracto, cancelación, garantía—, que cuentan contra su tope.
 */
public class PagoConPedidoQueLoEsperaException extends RuntimeException {

  public PagoConPedidoQueLoEsperaException(UUID pagoId) {
    super(
        "El pago "
            + pagoId
            + " pertenece a su pedido: se devuelve por los caminos del pedido, no como pago sin"
            + " pedido.");
  }
}
