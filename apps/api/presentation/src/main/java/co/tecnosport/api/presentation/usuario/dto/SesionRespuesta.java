package co.tecnosport.api.presentation.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * El token de refresco no va aquí: viaja en la cookie {@code HttpOnly} (docs/08-seguridad-legal.md
 * — el de acceso sí, porque vive en memoria en el cliente, nunca en localStorage).
 */
public record SesionRespuesta(
    UUID usuarioId,
    String rol,
    String accessToken,
    /**
     * ADR-0096: el token solo sirve para activar el segundo factor. El panel lleva a la persona a
     * activarlo antes que a ninguna otra parte; el servidor, mientras tanto, le responde 403 en
     * {@code /admin/**}.
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean segundoFactorPorActivar) {}
