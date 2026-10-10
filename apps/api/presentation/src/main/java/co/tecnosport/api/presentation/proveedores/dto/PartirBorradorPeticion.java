package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @param fotos las fotos de la publicación que se lleva el borrador nuevo, por su {@code mensajeId}
 */
public record PartirBorradorPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> fotos) {

  public PartirBorradorPeticion {
    Objects.requireNonNull(fotos, "Partir dice qué fotos se van.");
    fotos = List.copyOf(fotos);
  }
}
