package co.tecnosport.api.presentation.envio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Las variantes del carrito que se va a pagar. Sin cantidades: de dónde viene cada artículo no
 * depende de cuántos se lleven. Una lista ausente o con un hueco es un cuerpo mal formado (422), no
 * un carrito sin recogida.
 */
public record ModalidadesDeEntregaRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> varianteIds) {

  public ModalidadesDeEntregaRequest {
    Objects.requireNonNull(varianteIds, "Las variantes del carrito son obligatorias.");
    if (varianteIds.contains(null)) {
      throw new IllegalArgumentException("Una variante del carrito no puede ir vacía.");
    }
    varianteIds = List.copyOf(varianteIds);
  }
}
