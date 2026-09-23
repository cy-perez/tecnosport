package co.tecnosport.api.application.usuario;

import java.util.UUID;

/**
 * {@code usuarioId} no viaja en el cuerpo de la petición: lo pone el controlador desde el token de
 * acceso ya verificado. Nadie cambia la clave de otro por este camino.
 */
public record CambiarClaveComando(
    UUID usuarioId, String claveActualTextoPlano, String claveNuevaTextoPlano) {}
