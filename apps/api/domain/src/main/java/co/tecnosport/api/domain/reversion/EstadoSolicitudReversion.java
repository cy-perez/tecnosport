package co.tecnosport.api.domain.reversion;

/**
 * Radicada, gestionada ante el emisor, resuelta.
 *
 * <p>{@code GESTIONADA} existe porque los términos publicados prometen algo concreto y verificable:
 * "nosotros facilitamos el trámite". Facilitarlo implica que exista un camino y que quede escrito
 * qué se hizo — sin ese estado, la promesa sería indemostrable, que es la forma más silenciosa de
 * incumplirla.
 */
public enum EstadoSolicitudReversion {
  RADICADA,
  GESTIONADA,
  RESUELTA
}
