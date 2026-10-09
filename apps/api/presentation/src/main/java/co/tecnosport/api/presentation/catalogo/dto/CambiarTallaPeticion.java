package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/** La talla como tiene que quedar el modelo, en todos sus colores. */
public record CambiarTallaPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String talla) {

  public CambiarTallaPeticion {
    Objects.requireNonNull(talla, "Falta la talla.");
  }
}
