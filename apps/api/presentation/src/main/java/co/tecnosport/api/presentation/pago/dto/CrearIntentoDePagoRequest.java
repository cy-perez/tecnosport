package co.tecnosport.api.presentation.pago.dto;

import java.util.UUID;

public record CrearIntentoDePagoRequest(UUID pedidoId) {

  public CrearIntentoDePagoRequest {
    if (pedidoId == null) {
      throw new IllegalArgumentException("pedidoId es obligatorio.");
    }
  }
}
