package co.tecnosport.api.application.pedido;

/**
 * Qué hizo una vuelta de la tarea que manda los comprobantes de compra.
 *
 * <p>{@code pendientes} son los pedidos en firme que la consulta trajo sin comprobante, {@code
 * enviados} aquellos cuyo reclamo ganó esta instancia y cuyo correo salió sin lanzar, y {@code
 * fallidos} aquellos cuyo envío lanzó y a los que se les devolvió el reclamo para reintentarlo en
 * la vuelta siguiente.
 *
 * <p>Los tres, y no solo el segundo: la diferencia entre {@code pendientes} y los otros dos es otra
 * instancia habiéndose adelantado, y sin separar {@code fallidos} el registro afirmaba haber
 * enviado lo que no envió. Lo levantó una revisión adversarial.
 *
 * <p><b>{@code enviados} quiere decir "encolado", no "llegó"</b>, y conviene no olvidarlo. Este
 * párrafo decía algo peor —que el adaptador se tragaba los fallos de SMTP y que por eso un envío
 * fallido se contaba como enviado— y dejó de ser cierto en {@code adr/0044}. Con {@code adr/0045}
 * lo que hay es una bandeja de salida: mandarlo de verdad, y reintentarlo, es de {@code
 * DrenarBandejaDeSalida}, y quien quiera saber si un comprobante llegó tiene que mirar ahí.
 */
public record ResultadoComprobantes(int pendientes, int enviados, int fallidos) {}
