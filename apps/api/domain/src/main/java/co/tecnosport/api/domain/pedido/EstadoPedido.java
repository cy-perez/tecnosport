package co.tecnosport.api.domain.pedido;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Grafo de transiciones válidas del diagrama de docs/02-modelo-datos.md. Un {@code ENTREGADO} tiene
 * dos salidas posibles ({@code DEVUELTO} para pago en línea, {@code RECAUDO_PENDIENTE} para
 * contraentrega) porque el diagrama no separa el estado por método de pago; quien orquesta la
 * transición es responsable de no mezclar los dos caminos.
 *
 * <p>{@code RECAUDO_CONCILIADO} también sale hacia {@code DEVUELTO}, y no es una simetría
 * decorativa: el retracto del artículo 47 de la Ley 1480 de 2011 no distingue el método de pago,
 * así que un contraentrega ya entregado y recaudado puede devolverse igual que uno pagado en línea.
 * Sin esa arista, el único camino de vuelta era el de pago en línea y la mitad de las compras no
 * tenía a dónde ir.
 */
public enum EstadoPedido {
  CREADO,
  PAGO_PENDIENTE,
  PAGADO,
  PAGO_FALLIDO,
  CONFIRMADO_CONTRAENTREGA,
  EN_PREPARACION,
  DESPACHADO,
  ENTREGADO,
  RECHAZADO_EN_ENTREGA,
  DEVUELTO,
  RECAUDO_PENDIENTE,
  RECAUDO_CONCILIADO,

  /**
   * El pedido se cancela antes de despachar, por causa del negocio y no del comprador: la
   * existencia desapareció después de la compra, o no se entregó dentro del plazo pactado y el
   * comprador terminó el contrato. Los dos casos están prometidos en los términos publicados y
   * ninguno tenía a dónde ir en este grafo.
   *
   * <p>Solo antes de despachar. Después de que la mercancía sale ya existen los caminos que
   * corresponden —entrega, rechazo en la entrega, devolución— y añadir aquí un atajo los
   * duplicaría.
   */
  CANCELADO;

  private static final Map<EstadoPedido, Set<EstadoPedido>> TRANSICIONES_VALIDAS =
      new EnumMap<>(EstadoPedido.class);

  static {
    TRANSICIONES_VALIDAS.put(CREADO, EnumSet.of(PAGO_PENDIENTE, CONFIRMADO_CONTRAENTREGA));
    TRANSICIONES_VALIDAS.put(PAGO_PENDIENTE, EnumSet.of(PAGADO, PAGO_FALLIDO, CANCELADO));
    TRANSICIONES_VALIDAS.put(PAGO_FALLIDO, EnumSet.of(PAGO_PENDIENTE));
    TRANSICIONES_VALIDAS.put(PAGADO, EnumSet.of(EN_PREPARACION, CANCELADO));
    TRANSICIONES_VALIDAS.put(CONFIRMADO_CONTRAENTREGA, EnumSet.of(EN_PREPARACION, CANCELADO));
    TRANSICIONES_VALIDAS.put(EN_PREPARACION, EnumSet.of(DESPACHADO, CANCELADO));
    TRANSICIONES_VALIDAS.put(DESPACHADO, EnumSet.of(ENTREGADO, RECHAZADO_EN_ENTREGA));
    TRANSICIONES_VALIDAS.put(ENTREGADO, EnumSet.of(DEVUELTO, RECAUDO_PENDIENTE));
    TRANSICIONES_VALIDAS.put(RECHAZADO_EN_ENTREGA, EnumSet.noneOf(EstadoPedido.class));
    TRANSICIONES_VALIDAS.put(DEVUELTO, EnumSet.noneOf(EstadoPedido.class));
    TRANSICIONES_VALIDAS.put(RECAUDO_PENDIENTE, EnumSet.of(RECAUDO_CONCILIADO));
    TRANSICIONES_VALIDAS.put(RECAUDO_CONCILIADO, EnumSet.of(DEVUELTO));
    TRANSICIONES_VALIDAS.put(CANCELADO, EnumSet.noneOf(EstadoPedido.class));
  }

  public boolean puedeTransicionarA(EstadoPedido siguiente) {
    return TRANSICIONES_VALIDAS.get(this).contains(siguiente);
  }
}
