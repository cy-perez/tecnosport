package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * La clave TOTP en Base32 —para teclearla en la aplicación— y la URI {@code otpauth://} —para
 * pegarla o abrirla en el teléfono—. Se muestran una sola vez (ADR-0096).
 */
public record ActivacionSegundoFactorRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String secreto,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String uri,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int digitos,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int periodoSegundos) {}
