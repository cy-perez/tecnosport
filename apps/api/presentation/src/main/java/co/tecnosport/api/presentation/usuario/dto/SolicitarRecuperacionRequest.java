package co.tecnosport.api.presentation.usuario.dto;

public record SolicitarRecuperacionRequest(String correo) {

  public SolicitarRecuperacionRequest {
    if (correo == null || correo.isBlank()) {
      throw new IllegalArgumentException("correo es obligatorio.");
    }
  }
}
