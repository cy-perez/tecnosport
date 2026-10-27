package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Cuántas variantes cambiaron de precio publicado, y cuántas de la lista revisada se dejaron como
 * estaban porque cambiaron entre la revisión y la aplicación: esas hay que revisarlas de nuevo.
 */
public record RecalculoAplicadoRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) int recalculadas,
    @Schema(requiredMode = RequiredMode.REQUIRED) int omitidas) {}
