package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.Rol;
import java.util.UUID;

/**
 * Lo que trae un JWT de acceso válido, ya verificado — nada más de él le importa a la aplicación.
 */
public record ClaimsAcceso(UUID usuarioId, Rol rol) {}
