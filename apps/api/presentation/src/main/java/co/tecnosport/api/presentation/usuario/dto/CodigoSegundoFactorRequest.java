package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Seis dígitos de la aplicación de autenticación o, donde se acepte, un código de respaldo. El
 * formato lo juzga el dominio: aquí solo se exige que venga.
 */
public record CodigoSegundoFactorRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String codigo) {

  public CodigoSegundoFactorRequest {
    if (codigo == null || codigo.isBlank()) {
      throw new IllegalArgumentException("codigo es obligatorio.");
    }
  }
}
