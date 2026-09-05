package co.tecnosport.api.presentation.usuario.dto;

public record RegistrarUsuarioRequest(String correo, String clave) {

  public RegistrarUsuarioRequest {
    if (correo == null || correo.isBlank()) {
      throw new IllegalArgumentException("correo es obligatorio.");
    }
    if (clave == null || clave.isBlank()) {
      throw new IllegalArgumentException("clave es obligatoria.");
    }
  }
}
