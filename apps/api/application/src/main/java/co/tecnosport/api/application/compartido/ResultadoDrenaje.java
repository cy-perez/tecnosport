package co.tecnosport.api.application.compartido;

/**
 * Qué hizo una vuelta del drenaje de la bandeja de salida.
 *
 * <p>{@code rendidos} es el único de los cinco que merece un registro en {@code error}: los otros
 * cuatro son el mecanismo funcionando. Un correo fallido se va a reintentar; uno rendido ya no, y
 * si era un comprobante de compra hay alguien sin el documento de su venta.
 *
 * <p>{@code pendientes} y {@code enviados} no tienen por qué coincidir aunque no falle nada: la
 * diferencia es otra instancia habiéndose adelantado en el reclamo. Es la misma distinción que
 * {@code ResultadoComprobantes} y por la misma razón.
 */
public record ResultadoDrenaje(
    int pendientes, int enviados, int fallidos, int rendidos, int purgados) {}
