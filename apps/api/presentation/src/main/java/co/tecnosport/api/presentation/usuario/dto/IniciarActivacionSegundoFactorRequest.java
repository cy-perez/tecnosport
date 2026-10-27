package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * La clave actual, aunque quien llama ya tenga sesión: un token de acceso robado no tiene que
 * bastar para atar la cuenta a un teléfono ajeno (ADR-0096).
 */
public record IniciarActivacionSegundoFactorRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String claveActual) {

  public IniciarActivacionSegundoFactorRequest {
    if (claveActual == null || claveActual.isBlank()) {
      throw new IllegalArgumentException("claveActual es obligatoria.");
    }
  }
}
