package co.tecnosport.api.presentation.carrito.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.UUID;

/**
 * El carrito con los precios de hoy. {@code precioUnitario} y {@code subtotal} de una línea faltan
 * cuando la variante ya no se vende.
 */
public record CarritoCotizadoRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) List<Linea> lineas,
    @Schema(requiredMode = RequiredMode.REQUIRED) DineroRespuesta subtotal) {

  public record Linea(
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID lineaId,
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID varianteId,
      @Schema(requiredMode = RequiredMode.REQUIRED) int cantidad,
      DineroRespuesta precioUnitario,
      DineroRespuesta subtotal,
      @Schema(requiredMode = RequiredMode.REQUIRED) boolean disponible) {}
}
