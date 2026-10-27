package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * La respuesta 202 del inicio de sesión de una cuenta con segundo factor (ADR-0096): la clave fue
 * correcta y falta el código. Sin cookie ni token: el desafío no abre nada por sí mismo, y vive en
 * la memoria del cliente hasta {@code venceEn}.
 */
public record DesafioSegundoFactorRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID desafio,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant venceEn) {}
