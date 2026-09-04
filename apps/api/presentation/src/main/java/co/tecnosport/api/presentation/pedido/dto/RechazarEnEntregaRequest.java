package co.tecnosport.api.presentation.pedido.dto;

public record RechazarEnEntregaRequest(String motivo) {

  public RechazarEnEntregaRequest {
    if (motivo == null || motivo.isBlank()) {
      throw new IllegalArgumentException("motivo es obligatorio.");
    }
  }
}
