package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pedido.MetodoPago;

/** Un pedido de tarjeta no se cobra por Sistecrédito, ni al revés. */
public class MetodoDePagoNoEsDeSistecreditoException extends RuntimeException {

  public MetodoDePagoNoEsDeSistecreditoException(MetodoPago metodoPago) {
    super("El método de pago " + metodoPago + " no lo cobra Sistecrédito.");
  }
}
