package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * El estado del segundo factor de quien pregunta (ADR-0096). {@code activadoEn} es nulo si no está
 * activo; una activación pendiente cuenta como inactiva. Con {@code obligatorio}, el panel no
 * ofrece desactivarlo: el servidor respondería 409.
 */
public record EstadoSegundoFactorRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean activo,
    Instant activadoEn,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int codigosDeRespaldoRestantes,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean obligatorio) {}
