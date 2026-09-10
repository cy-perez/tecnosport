package co.tecnosport.api.domain.atencion;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Una solicitud radicada se responde, con o sin prórroga por el camino. No hay estado "cerrada sin
 * responder": una solicitud que nadie contestó sigue abierta, y tiene que seguir apareciendo en la
 * bandeja hasta que alguien la conteste. Ocultarla sería justo lo contrario de lo que hace falta
 * demostrar.
 */
public enum EstadoSolicitudAtencion {
  RADICADA,
  PRORROGADA,
  RESPONDIDA;

  private static final Map<EstadoSolicitudAtencion, Set<EstadoSolicitudAtencion>> TRANSICIONES =
      new EnumMap<>(EstadoSolicitudAtencion.class);

  static {
    TRANSICIONES.put(RADICADA, EnumSet.of(PRORROGADA, RESPONDIDA));
    TRANSICIONES.put(PRORROGADA, EnumSet.of(RESPONDIDA));
    TRANSICIONES.put(RESPONDIDA, EnumSet.noneOf(EstadoSolicitudAtencion.class));
  }

  public boolean puedeTransicionarA(EstadoSolicitudAtencion siguiente) {
    return TRANSICIONES.get(this).contains(siguiente);
  }
}
