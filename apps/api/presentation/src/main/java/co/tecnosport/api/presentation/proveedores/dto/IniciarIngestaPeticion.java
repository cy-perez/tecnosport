package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * @param objectKey la key que devolvió la solicitud de subida
 * @param nombreArchivo el nombre con que se eligió el zip, para el historial; opcional
 */
public record IniciarIngestaPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String objectKey, String nombreArchivo) {

  public IniciarIngestaPeticion {
    Objects.requireNonNull(objectKey, "Hay que decir qué archivo se subió.");
  }
}
