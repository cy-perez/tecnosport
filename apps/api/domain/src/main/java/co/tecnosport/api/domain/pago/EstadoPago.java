package co.tecnosport.api.domain.pago;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de una transacción Wompi (docs/11-pagos-y-envios.md): la aprobación nunca la decide el
 * navegador, solo el webhook firmado o una consulta directa a la pasarela. Los tres estados de
 * salida son finales aquí — un reintento de pago abre un {@link Pago} nuevo con otra referencia, no
 * reabre este.
 */
public enum EstadoPago {
  PENDIENTE,
  APROBADO,
  RECHAZADO,
  ERROR;

  private static final Map<EstadoPago, Set<EstadoPago>> TRANSICIONES_VALIDAS =
      new EnumMap<>(EstadoPago.class);

  static {
    TRANSICIONES_VALIDAS.put(PENDIENTE, EnumSet.of(APROBADO, RECHAZADO, ERROR));
    TRANSICIONES_VALIDAS.put(APROBADO, EnumSet.noneOf(EstadoPago.class));
    TRANSICIONES_VALIDAS.put(RECHAZADO, EnumSet.noneOf(EstadoPago.class));
    TRANSICIONES_VALIDAS.put(ERROR, EnumSet.noneOf(EstadoPago.class));
  }

  public boolean puedeTransicionarA(EstadoPago siguiente) {
    return TRANSICIONES_VALIDAS.get(this).contains(siguiente);
  }
}
