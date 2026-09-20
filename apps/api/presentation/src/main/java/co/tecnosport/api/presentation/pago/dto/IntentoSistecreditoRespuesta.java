package co.tecnosport.api.presentation.pago.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;

/**
 * A diferencia de {@link IntentoDePagoRespuesta}, aquí no viaja ninguna llave pública ni ninguna
 * firma: la URL la arma la pasarela y llega hecha, así que no hay nada que el navegador tenga que
 * componer y, por tanto, nada que pueda alterar por el camino.
 *
 * <p>Es de un solo uso y la transacción vive unos 15 minutos.
 */
public record IntentoSistecreditoRespuesta(
    String referencia, DineroRespuesta monto, String urlRedireccion) {}
