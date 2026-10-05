package co.tecnosport.api.presentation.pago.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/** Sin monto: es el del pago, entero, y lo pone el servidor. */
public record RegistrarReintegroDePagoSinPedidoRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) String medio, String comprobante) {

  public RegistrarReintegroDePagoSinPedidoRequest {
    Objects.requireNonNull(medio, "El medio del reintegro es obligatorio.");
  }
}
