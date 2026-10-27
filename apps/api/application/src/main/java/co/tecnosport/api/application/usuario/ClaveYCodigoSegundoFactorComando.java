package co.tecnosport.api.application.usuario;

import java.util.UUID;

/**
 * Lo que piden las dos operaciones que debilitan el segundo factor —regenerar los códigos y
 * desactivarlo—: la clave actual y un código (de la aplicación o de respaldo). {@code usuarioId}
 * sale del token de acceso.
 */
public record ClaveYCodigoSegundoFactorComando(
    UUID usuarioId, String claveActualTextoPlano, String codigo) {}
