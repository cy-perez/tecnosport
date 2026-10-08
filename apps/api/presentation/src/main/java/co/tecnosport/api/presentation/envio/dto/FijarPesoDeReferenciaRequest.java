package co.tecnosport.api.presentation.envio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * El peso promedio en gramos. Primitivo por el mismo motivo que {@link
 * FijarMedidasDeReferenciaRequest}; para quitar el promedio está el {@code DELETE}, no un cero.
 */
public record FijarPesoDeReferenciaRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) int pesoGramos) {}
