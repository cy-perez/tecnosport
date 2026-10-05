package co.tecnosport.api.presentation.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * El correo que autoriza el seguimiento, en el cuerpo y no en la URL: en la URL quedaba escrito en
 * los registros de los dos servicios de Cloud Run, en el historial del navegador y en el de los
 * terceros que lo recibían. Mismo criterio que {@link SeguimientoPorNumeroRequest}.
 */
public record SeguimientoPorIdRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String correo) {

  public SeguimientoPorIdRequest {
    Objects.requireNonNull(correo, "El correo es obligatorio.");
  }
}
