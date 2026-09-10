package co.tecnosport.api.domain.retracto;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * El recorrido de una solicitud de retracto, que no es el del pedido: el pedido llega a {@code
 * DEVUELTO} y ahí se queda, mientras la solicitud sigue viva hasta que el dinero salga.
 *
 * <p>{@code PRODUCTO_RECIBIDO} es un estado propio y no un detalle porque marca el momento en que
 * la obligación cambia de lado: hasta ahí esperamos al comprador; a partir de ahí el plazo de
 * quince días calendario del reintegro (art. 47, modificado por la Ley 2439 de 2024) corre contra
 * el negocio.
 */
public enum EstadoSolicitudRetracto {
  RADICADA,
  PRODUCTO_RECIBIDO,
  REEMBOLSADA,
  RECHAZADA;

  private static final Map<EstadoSolicitudRetracto, Set<EstadoSolicitudRetracto>> VALIDAS =
      new EnumMap<>(EstadoSolicitudRetracto.class);

  static {
    VALIDAS.put(RADICADA, EnumSet.of(PRODUCTO_RECIBIDO, RECHAZADA));
    VALIDAS.put(PRODUCTO_RECIBIDO, EnumSet.of(REEMBOLSADA, RECHAZADA));
    VALIDAS.put(REEMBOLSADA, EnumSet.noneOf(EstadoSolicitudRetracto.class));
    VALIDAS.put(RECHAZADA, EnumSet.noneOf(EstadoSolicitudRetracto.class));
  }

  public boolean puedeTransicionarA(EstadoSolicitudRetracto siguiente) {
    return VALIDAS.get(this).contains(siguiente);
  }
}
