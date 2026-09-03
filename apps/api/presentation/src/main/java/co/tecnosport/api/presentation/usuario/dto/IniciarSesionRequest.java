package co.tecnosport.api.presentation.usuario.dto;

public record IniciarSesionRequest(String correo, String clave) {

  public IniciarSesionRequest {
    if (correo == null || correo.isBlank()) {
      throw new IllegalArgumentException("correo es obligatorio.");
    }
    if (clave == null || clave.isBlank()) {
      throw new IllegalArgumentException("clave es obligatoria.");
    }
  }
}
