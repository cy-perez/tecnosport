package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.MetodoPago;

public final class MetodoDePagoNoEsTransferenciaManualException extends RuntimeException {

  public MetodoDePagoNoEsTransferenciaManualException(MetodoPago metodoPago) {
    super(
        "El pedido tiene método de pago "
            + metodoPago
            + ", no se puede conciliar como transferencia manual.");
  }
}
