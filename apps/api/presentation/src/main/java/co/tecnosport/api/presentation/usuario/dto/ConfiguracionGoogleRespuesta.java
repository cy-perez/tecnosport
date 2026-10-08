package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo que el sitio necesita para pintar el botón de Google: si está habilitado en este ambiente, el
 * identificador público del cliente —no es un secreto, va en la página de todos modos— y de dónde
 * se carga el script. Las dos URL salen de configuración y no de una constante del frontend (regla
 * 5).
 */
public record ConfiguracionGoogleRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean habilitado,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String clienteId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String urlScript) {}
