package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * La sesión nueva —regenerar revoca las demás— y los diez códigos nuevos, en claro y por única vez
 * (ADR-0096). El token de refresco va en la cookie.
 */
public record CodigosDeRespaldoRegeneradosRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SesionRespuesta sesion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> codigosDeRespaldo) {}
