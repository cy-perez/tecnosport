package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * El nombre nuevo de una marca. El resto de las reglas —vacío, largo, espacios— las aplica {@code
 * Marca}; aquí solo que venga, con las dos piezas que pide {@code apps/api/CLAUDE.md}: la guarda,
 * que protege, y el {@code @Schema}, que hace que el contrato lo diga.
 */
public record EditarMarcaPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nombre) {

  public EditarMarcaPeticion {
    Objects.requireNonNull(nombre, "El nombre de la marca es obligatorio.");
  }
}
