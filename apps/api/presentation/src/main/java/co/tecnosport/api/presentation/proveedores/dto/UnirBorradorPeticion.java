package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/**
 * @param borradorId el otro borrador de la misma publicación, el que se une a este y desaparece
 */
public record UnirBorradorPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID borradorId) {

  public UnirBorradorPeticion {
    Objects.requireNonNull(borradorId, "Unir dice con cuál.");
  }
}
