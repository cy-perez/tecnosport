package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Regenerar los códigos de respaldo y desactivar el segundo factor piden las dos cosas: la clave
 * actual y un código, de la aplicación o de respaldo (ADR-0096).
 */
public record ClaveYCodigoSegundoFactorRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String claveActual,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String codigo) {

  public ClaveYCodigoSegundoFactorRequest {
    if (claveActual == null || claveActual.isBlank()) {
      throw new IllegalArgumentException("claveActual es obligatoria.");
    }
    if (codigo == null || codigo.isBlank()) {
      throw new IllegalArgumentException("codigo es obligatorio.");
    }
  }
}
