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
 * <p><b>Un envío que falla en silencio no cuenta aquí</b>, y conviene no olvidarlo: el adaptador de
 * producción se traga los fallos de SMTP sin relanzarlos (ver {@code EnviadorDeCorreo}), así que
 * ese caso se contabiliza como enviado. Mientras no exista la bandeja de salida que ese javadoc
 * lleva pidiendo desde la Fase 4, este número dice "se intentó", no "llegó".
 */
public record ResultadoComprobantes(int pendientes, int enviados, int fallidos) {}
