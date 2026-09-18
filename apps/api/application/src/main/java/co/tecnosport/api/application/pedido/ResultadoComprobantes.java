package co.tecnosport.api.application.pedido;

/**
 * Qué hizo una vuelta de la tarea que manda los comprobantes de compra.
 *
 * <p>{@code pendientes} son los pedidos en firme que la consulta trajo sin comprobante, y {@code
 * enviados} aquellos cuyo reclamo ganó esta instancia. La diferencia entre los dos no es un error:
 * es otra instancia habiéndose adelantado. Verlos separados es lo único que distingue "no había
 * nada que mandar" de "otro los mandó".
 */
public record ResultadoComprobantes(int pendientes, int enviados) {}
