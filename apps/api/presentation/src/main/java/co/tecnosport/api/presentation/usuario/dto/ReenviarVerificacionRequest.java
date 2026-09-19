package co.tecnosport.api.presentation.usuario.dto;

public record ReenviarVerificacionRequest(String correo) {

  public ReenviarVerificacionRequest {
    if (correo == null || correo.isBlank()) {
      throw new IllegalArgumentException("correo es obligatorio.");
    }
  }
}
