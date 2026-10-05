package co.tecnosport.api.application.pago;

import java.util.UUID;

/** Ya hay un reintegro para este pago: devolverlo otra vez sería pagar dos veces. */
public class PagoSinPedidoYaDevueltoException extends RuntimeException {

  public PagoSinPedidoYaDevueltoException(UUID pagoId) {
    super("El pago " + pagoId + " ya tiene registrado su reintegro.");
  }
}
