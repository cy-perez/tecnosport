package co.tecnosport.api.presentation.difusion.dto;

/**
 * El texto propuesto para la caja editable del panel.
 *
 * <p>Un record y no una cadena suelta: un endpoint que devuelve `text/plain` obliga al cliente
 * generado a tratarlo aparte de todos los demás, y el día que esto lleve algo más —cuántos
 * caracteres caben en la red elegida, por ejemplo— habría que cambiar el contrato entero.
 */
public record PropuestaDePieRespuesta(String pieDeFoto) {}
