package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/** El precio de venta de una variante, en pesos enteros, como el de {@code AgregarVariante}. */
public record PrecioDeVariantePeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID varianteId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long precio) {

  public PrecioDeVariantePeticion {
    Objects.requireNonNull(varianteId, "Falta la variante.");
  }
}
