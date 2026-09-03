package co.tecnosport.api.presentation.pedido.dto;

public record VerificarContraentregaRequest(String motivo) {

  public VerificarContraentregaRequest {
    if (motivo == null || motivo.isBlank()) {
      throw new IllegalArgumentException("motivo es obligatorio.");
    }
  }
}
