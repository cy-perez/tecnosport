package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.UUID;

/**
 * Una variante cuyo publicado no es el que dan las tarifas de hoy, en pesos enteros: el que tiene y
 * el que tendría (ADR-0095).
 */
public record PrecioPorRecalcularRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) UUID varianteId,
    @Schema(requiredMode = RequiredMode.REQUIRED) String sku,
    @Schema(requiredMode = RequiredMode.REQUIRED) String nombreProducto,
    @Schema(requiredMode = RequiredMode.REQUIRED) long precioContado,
    @Schema(requiredMode = RequiredMode.REQUIRED) long publicadoActual,
    @Schema(requiredMode = RequiredMode.REQUIRED) long publicadoNuevo) {}
