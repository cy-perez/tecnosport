package co.tecnosport.api.presentation.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/** La variante —configuración y color— que el proveedor confirmó tener. */
public record GenerarEnlaceDeCompraRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID varianteId) {

  public GenerarEnlaceDeCompraRequest {
    Objects.requireNonNull(varianteId, "El enlace de compra es de una variante.");
  }
}
