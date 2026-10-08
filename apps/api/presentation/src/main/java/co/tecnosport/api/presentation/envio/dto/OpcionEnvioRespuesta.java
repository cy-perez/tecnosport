package co.tecnosport.api.presentation.envio.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una transportadora con su tarifa más económica para este carrito y destino (ADR-0073). Sin el
 * identificador de la tarifa, por lo mismo que la respuesta que la contiene: lo que el cliente
 * devuelve al crear el pedido es el nombre de la transportadora, nunca el costo ni el {@code
 * rate_id}.
 */
public record OpcionEnvioRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String transportadora,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DineroRespuesta costoEnvio,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int diasEstimados) {}
