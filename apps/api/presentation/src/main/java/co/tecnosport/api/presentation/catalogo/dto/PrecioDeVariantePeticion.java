package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/**
 * El precio <b>de contado</b> de una variante, en pesos enteros, como el de {@code
 * AgregarVariante}. El publicado no viaja: lo calcula el servidor (ADR-0095).
 */
public record PrecioDeVariantePeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID varianteId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long precioContado) {

  public PrecioDeVariantePeticion {
    Objects.requireNonNull(varianteId, "Falta la variante.");
  }
}
