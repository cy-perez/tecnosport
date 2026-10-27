package co.tecnosport.api.presentation.carrito.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;
import java.util.UUID;

/**
 * El carrito con los precios de hoy. {@code precioUnitario} y {@code subtotal} de una línea faltan
 * cuando la variante ya no se vende.
 *
 * <p>{@code precioUnitario} y {@code subtotal} son el precio publicado, el que se paga con pasarela
 * (ADR-0095). {@code precioContado} y {@code subtotalContado}, el que se paga sin ella: por
 * transferencia, en el punto o contra entrega. Faltan donde faltan los publicados.
 */
public record CarritoCotizadoRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) List<Linea> lineas,
    @Schema(requiredMode = RequiredMode.REQUIRED) DineroRespuesta subtotal,
    @Schema(requiredMode = RequiredMode.REQUIRED) DineroRespuesta subtotalContado) {

  public record Linea(
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID lineaId,
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID varianteId,
      @Schema(requiredMode = RequiredMode.REQUIRED) int cantidad,
      DineroRespuesta precioUnitario,
      DineroRespuesta subtotal,
      @Schema(requiredMode = RequiredMode.REQUIRED) boolean disponible,
      DineroRespuesta precioContado,
      DineroRespuesta subtotalContado) {}
}
