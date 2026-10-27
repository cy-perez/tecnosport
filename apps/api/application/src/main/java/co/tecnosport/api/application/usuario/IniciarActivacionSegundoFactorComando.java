package co.tecnosport.api.application.usuario;

import java.util.UUID;

/**
 * {@code usuarioId} sale del token de acceso, nunca del cuerpo: nadie activa el segundo factor de
 * otra cuenta.
 */
public record IniciarActivacionSegundoFactorComando(UUID usuarioId, String claveActualTextoPlano) {}
