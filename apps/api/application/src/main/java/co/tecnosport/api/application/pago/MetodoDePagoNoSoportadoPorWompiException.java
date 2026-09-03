package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pedido.MetodoPago;

public final class MetodoDePagoNoSoportadoPorWompiException extends RuntimeException {

  public MetodoDePagoNoSoportadoPorWompiException(MetodoPago metodoPago) {
    super("El método de pago " + metodoPago + " no se procesa a través de Wompi.");
  }
}
