package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/**
 * Una fila de la previsualización del recálculo tal como la vio quien lo confirma, en pesos
 * enteros: los mismos campos que {@link PrecioPorRecalcularRespuesta} sin el SKU ni el nombre. El
 * servidor solo la graba si la variante sigue así y la tarifa de hoy da el mismo publicado nuevo.
 */
public record PrecioRevisadoPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID varianteId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long precioContado,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long publicadoActual,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long publicadoNuevo) {

  public PrecioRevisadoPeticion {
    Objects.requireNonNull(varianteId, "Falta la variante.");
  }
}
