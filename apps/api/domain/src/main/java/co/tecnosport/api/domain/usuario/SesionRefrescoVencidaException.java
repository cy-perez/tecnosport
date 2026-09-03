package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;

/**
 * Distinta de {@link SesionRefrescoReutilizadaException} a propósito: una sesión vencida sin usar
 * es solo "hay que iniciar sesión de nuevo", no una señal de robo — no invalida la familia.
 */
public final class SesionRefrescoVencidaException extends ExcepcionDeDominio {

  public SesionRefrescoVencidaException(UUID sesionId) {
    super("La sesión de refresco " + sesionId + " ya venció.");
  }
}
