package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * La sesión nueva —la activación revoca las demás— y los diez códigos de respaldo, en claro y por
 * única vez (ADR-0096). El token de refresco va en la cookie, como en el inicio de sesión.
 */
public record ActivacionConfirmadaRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SesionRespuesta sesion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> codigosDeRespaldo) {}
