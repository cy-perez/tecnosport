package co.tecnosport.api.presentation.compartido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Los dos campos siempre vienen. Sin el {@code @Schema} el contrato los publicaba como opcionales,
 * y el frontend los rellenaba con {@code ?? 0}: un importe que faltara se pintaba como cero en vez
 * de fallar.
 */
public record DineroRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) long valor,
    @Schema(requiredMode = RequiredMode.REQUIRED) String moneda) {}
