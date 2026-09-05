package co.tecnosport.api.presentation.usuario.dto;

public record VerificarCorreoRequest(String token) {

  public VerificarCorreoRequest {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token es obligatorio.");
    }
  }
}
