package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

/** La lista que la persona revisó y confirmó; lo que no venga no se toca. */
public record AplicarRecalculoPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PrecioRevisadoPeticion> variantes) {

  public AplicarRecalculoPeticion {
    Objects.requireNonNull(variantes, "Faltan las variantes revisadas.");
    variantes = List.copyOf(variantes);
  }
}
