package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

/** Los colores oficiales del modelo, en el orden del fabricante; vacía la quita. */
public record DefinirPaletaPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> colores) {

  public DefinirPaletaPeticion {
    Objects.requireNonNull(colores, "La paleta trae sus colores, aunque sean ninguno.");
  }
}
