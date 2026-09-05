package co.tecnosport.api.presentation.usuario.dto;

public record ConfirmarRecuperacionRequest(String token, String claveNueva) {

  public ConfirmarRecuperacionRequest {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token es obligatorio.");
    }
    if (claveNueva == null || claveNueva.isBlank()) {
      throw new IllegalArgumentException("claveNueva es obligatoria.");
    }
  }
}
