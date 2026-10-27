package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** Cuántas variantes cambiaron de precio publicado. */
public record RecalculoAplicadoRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) int recalculadas) {}
