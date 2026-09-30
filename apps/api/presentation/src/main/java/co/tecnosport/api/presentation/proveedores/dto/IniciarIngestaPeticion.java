package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * @param objectKey la key que devolvió la solicitud de subida
 */
public record IniciarIngestaPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String objectKey) {

  public IniciarIngestaPeticion {
    Objects.requireNonNull(objectKey, "Hay que decir qué archivo se subió.");
  }
}
