package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param variantesNuevas las que nacieron para que cada color de la paleta se pueda elegir en cada
 *     configuración que ya se vende, sin existencia y con el color por confirmar
 */
public record PaletaDefinidaRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int variantesNuevas) {}
