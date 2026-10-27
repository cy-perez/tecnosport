package co.tecnosport.api.application.usuario;

/**
 * @param direccionIp la IP del cliente ya agrupada para el límite de intentos —una IPv6 por su
 *     /64—, tal como la resuelve presentation. Puede ser nula fuera de una petición HTTP; entonces
 *     todos esos intentos comparten el mismo cubo.
 */
public record IniciarSesionComando(String correo, String claveTextoPlano, String direccionIp) {}
