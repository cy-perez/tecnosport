package co.tecnosport.api.presentation.envio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Las variantes del carrito que se va a pagar. Sin cantidades: de dónde viene cada artículo no
 * depende de cuántos se lleven.
 */
public record ModalidadesDeEntregaRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> varianteIds) {}
