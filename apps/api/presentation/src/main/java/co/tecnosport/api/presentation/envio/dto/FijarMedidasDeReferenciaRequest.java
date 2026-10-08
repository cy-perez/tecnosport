package co.tecnosport.api.presentation.envio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Las tres medidas, obligatorias. Primitivos a propósito, como {@code MedirVariantePeticion}: un
 * campo ausente no es un estado legítimo, y con {@code int} Jackson 3 rechaza el cuerpo incompleto
 * antes de que llegue a ninguna parte. Que sean mayores que cero lo dice {@code
 * MedidasDeReferencia}; repetirlo aquí serían dos definiciones capaces de divergir. El
 * {@code @Schema} no valida nada: hace que el contrato publicado diga que son obligatorias.
 */
public record FijarMedidasDeReferenciaRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) int largoCm,
    @Schema(requiredMode = RequiredMode.REQUIRED) int anchoCm,
    @Schema(requiredMode = RequiredMode.REQUIRED) int altoCm) {}
