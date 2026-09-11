package co.tecnosport.api.presentation.pedido.dto;

import java.time.Instant;

/**
 * Los treinta días calendario para entregar (Ley 1480 de 2011, art. 18), tal como el panel los
 * necesita ver.
 *
 * <p>{@code limite} y {@code verdicto} los calcula el servidor y no el navegador, por lo mismo que
 * el plazo de reintegro del retracto: son plazos legales y no pueden depender del reloj ni de la
 * zona horaria de quien mire la pantalla.
 *
 * <p>{@code verdicto} nunca es {@code INDETERMINADO} aquí, a diferencia del retracto y de las PQR:
 * este plazo cuenta días calendario, así que no hay festivos que puedan empujar el límite ni
 * incertidumbre que declarar.
 *
 * <p>{@code avisadoEn} es cuándo se le escribió al comprador para decirle que puede terminar el
 * contrato, o nulo si todavía no. Que esté vencido y sin aviso no es un estado imposible: el
 * vigilante pasa cada doce horas.
 *
 * <p>El objeto entero es nulo mientras el plazo no haya arrancado —un pago pendiente no tiene
 * contrato que incumplir—, que es distinto de "arrancó y va en plazo".
 *
 * <p><b>Viaja también por los dos endpoints públicos</b> que devuelven {@code PedidoRespuesta}
 * —crear el pedido y reintentar el pago—, y está decidido así: en contraentrega el plazo arranca al
 * confirmar, y hasta cuándo hay para entregarle es información de quien compró, no de operación. Lo
 * pinta {@code crearPedidoContraentregaQuedaConfirmadoSinPagoPendiente}, para que quede como
 * decisión y no como consecuencia de compartir el DTO.
 */
public record PlazoDeEntregaRespuesta(
    Instant inicio, Instant limite, String verdicto, Instant avisadoEn) {}
