package co.tecnosport.api.presentation.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/** Lo que el recálculo cambiaría; vacía si todos los publicados están al día. */
public record PreciosPorRecalcularRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) List<PrecioPorRecalcularRespuesta> variantes) {}
