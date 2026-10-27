package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * El desafío que devolvió el 202 del inicio de sesión y el código: seis dígitos de la aplicación o
 * uno de respaldo. El desafío viaja como texto y lo valida el caso de uso: uno mal formado responde
 * lo mismo que uno vencido.
 */
public record VerificarSegundoFactorRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String desafio,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String codigo) {

  public VerificarSegundoFactorRequest {
    if (desafio == null || desafio.isBlank()) {
      throw new IllegalArgumentException("desafio es obligatorio.");
    }
    if (codigo == null || codigo.isBlank()) {
      throw new IllegalArgumentException("codigo es obligatorio.");
    }
  }
}
