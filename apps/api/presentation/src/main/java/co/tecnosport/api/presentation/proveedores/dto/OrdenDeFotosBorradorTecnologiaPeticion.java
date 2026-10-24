package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * El orden nuevo de las fotos del borrador.
 *
 * @param fotoIds cada foto del borrador una sola vez; la primera es la principal
 */
public record OrdenDeFotosBorradorTecnologiaPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> fotoIds) {

  public OrdenDeFotosBorradorTecnologiaPeticion {
    Objects.requireNonNull(fotoIds, "Hay que decir en qué orden quedan las fotos.");
    fotoIds = List.copyOf(fotoIds);
  }
}
