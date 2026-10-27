package co.tecnosport.api.application.usuario;

import java.util.UUID;

/**
 * {@code usuarioId} sale del token de acceso; {@code codigo}, de la aplicación de autenticación.
 */
public record ConfirmarActivacionSegundoFactorComando(UUID usuarioId, String codigo) {}
