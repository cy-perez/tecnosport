package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.reintegro.MedioReintegro;
import java.util.Objects;
import java.util.UUID;

/** Sin monto: es el del pago, entero, y lo pone el caso de uso (regla dura #7). */
public record RegistrarReintegroDePagoSinPedidoComando(
    UUID pagoId, MedioReintegro medio, String comprobante, String actor) {

  public RegistrarReintegroDePagoSinPedidoComando {
    Objects.requireNonNull(pagoId, "El pago no puede ser nulo.");
    Objects.requireNonNull(medio, "El medio del reintegro no puede ser nulo.");
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
