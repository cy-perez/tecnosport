package co.tecnosport.api.domain.reversion;

/**
 * Las causales de la reversión del pago (Ley 1480 de 2011, art. 51). Son <b>tasadas</b>: no hay una
 * quinta, y esa es la diferencia de fondo con el retracto, que no necesita motivo.
 *
 * <p>Guardar cuál se invocó no es un detalle de reporte. La causal decide a quién le corresponde
 * responder —al emisor del medio de pago, al comercio, o a los dos—, qué prueba hace falta y qué
 * respuesta es la correcta. Un sistema que las meta todas en un estado genérico las pierde, y con
 * ellas la posibilidad de demostrar que se atendió lo que de verdad se pidió.
 */
public enum CausalReversion {
  /** El consumidor fue víctima de fraude. */
  FRAUDE,
  /** El producto no fue entregado. */
  PRODUCTO_NO_ENTREGADO,
  /** Lo entregado no corresponde a lo pedido. */
  PRODUCTO_NO_CORRESPONDE,
  /** El producto resultó defectuoso. */
  PRODUCTO_DEFECTUOSO
}
