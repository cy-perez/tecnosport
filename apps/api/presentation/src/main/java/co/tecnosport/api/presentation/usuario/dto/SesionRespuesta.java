package co.tecnosport.api.presentation.usuario.dto;

import java.util.UUID;

/**
 * El token de refresco no va aquí: viaja en la cookie {@code HttpOnly} (docs/08-seguridad-legal.md
 * — el de acceso sí, porque vive en memoria en el cliente, nunca en localStorage).
 */
public record SesionRespuesta(UUID usuarioId, String rol, String accessToken) {}
