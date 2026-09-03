package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;

/**
 * Una sesión de refresco ya usada (o ya revocada) vuelve a presentarse (docs/08-seguridad-legal.md:
 * "si un refresco ya usado reaparece, se invalida toda la familia de tokens de ese usuario"). Quien
 * orquesta interpreta esto como señal de robo del token, no como un error de solicitud — el dominio
 * solo la nombra, no decide la respuesta.
 */
public final class SesionRefrescoReutilizadaException extends ExcepcionDeDominio {

  public SesionRefrescoReutilizadaException(UUID sesionId) {
    super("La sesión de refresco " + sesionId + " ya fue usada o revocada.");
  }
}
