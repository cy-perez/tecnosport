package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * @param contentType el tipo con que el navegador va a subir el zip; tiene que ir en la firma
 */
public record SolicitarSubidaDeExportacionPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String contentType) {

  public SolicitarSubidaDeExportacionPeticion {
    Objects.requireNonNull(contentType, "Hay que decir el tipo de contenido del archivo.");
  }
}
