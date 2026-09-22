package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sin {@code usuarioId}: el del token de acceso es el único que vale. Aceptarlo en el cuerpo sería
 * ofrecer cambiarle la clave a otro.
 *
 * <p>El {@code @Schema} no valida nada -aqui no hay Bean Validation-: quien protege es el
 * constructor compacto. Lo que hace es que el contrato publicado, y con el el cliente TypeScript
 * generado, exijan los dos campos igual que los exige el servidor.
 */
public record CambiarClaveRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String claveActual,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String claveNueva) {

  public CambiarClaveRequest {
    if (claveActual == null || claveActual.isBlank()) {
      throw new IllegalArgumentException("claveActual es obligatoria.");
    }
    if (claveNueva == null || claveNueva.isBlank()) {
      throw new IllegalArgumentException("claveNueva es obligatoria.");
    }
  }
}
