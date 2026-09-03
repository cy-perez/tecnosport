package co.tecnosport.api.application.usuario;

import co.tecnosport.api.domain.usuario.Rol;
import java.util.UUID;

/**
 * {@code refreshTokenId} es el token de refresco tal cual: un identificador opaco, no un JWT — el
 * cliente lo guarda en la cookie {@code HttpOnly} y lo presenta sin más para refrescar
 * (docs/08-seguridad-legal.md).
 */
public record TokensDeSesion(UUID usuarioId, Rol rol, String accessToken, UUID refreshTokenId) {}
