package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/**
 * @param destinoId el borrador que recibe la foto
 */
public record MoverFotoPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID destinoId) {

  public MoverFotoPeticion {
    Objects.requireNonNull(destinoId, "Mover una foto dice a qué borrador va.");
  }
}
