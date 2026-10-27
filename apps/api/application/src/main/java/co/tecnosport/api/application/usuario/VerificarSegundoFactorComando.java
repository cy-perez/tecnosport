package co.tecnosport.api.application.usuario;

/**
 * @param desafio el valor que devolvió el inicio de sesión con la clave, tal como llegó; si no es
 *     un UUID, el desafío no es válido.
 * @param direccionIp ya agrupada para el límite —una IPv6 por su /64—, como en {@link
 *     IniciarSesionComando}.
 */
public record VerificarSegundoFactorComando(String desafio, String codigo, String direccionIp) {}
