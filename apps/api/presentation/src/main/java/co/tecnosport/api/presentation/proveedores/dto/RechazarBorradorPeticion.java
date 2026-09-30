package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

public record RechazarBorradorPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String motivo) {

  public RechazarBorradorPeticion {
    Objects.requireNonNull(motivo, "Rechazar exige el motivo.");
  }
}
