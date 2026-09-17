package co.tecnosport.api.application.envio;

/**
 * Lo que hizo una corrida de la resolución de emisiones, para el registro de la tarea programada.
 *
 * <p>{@code sinRespuesta} y {@code siguenEnCurso} se cuentan por separado aunque dejen la emisión
 * igual de abierta: uno es la plataforma sin contestar y el otro una transportadora tardando, y
 * sumarlos haría que un proveedor caído durante horas se leyera como paciencia.
 *
 * <p>{@code parciales} debería ser siempre cero. Cuando no lo sea, hay guías pagadas y vivas que
 * nadie va a usar hasta que una persona las mire.
 */
public record ResultadoResolucionEmisiones(
    int revisadas,
    int despachadas,
    int fallidas,
    int parciales,
    int siguenEnCurso,
    int sinRespuesta) {}
