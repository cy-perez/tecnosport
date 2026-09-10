package co.tecnosport.api.presentation.retracto.dto;

import java.time.Instant;

/**
 * {@code limiteDeReintegro} lo calcula el servidor y no el panel: es el plazo de quince dias
 * calendario del articulo 47 y no puede depender del reloj ni de la zona horaria del navegador de
 * quien mire la pantalla.
 *
 * <p>{@code verdictoAlRadicar} puede ser {@code INDETERMINADO}, y la pantalla tiene que decirlo
 * asi: sin el calendario de festivos cargado, el sistema no puede afirmar que un plazo vencio.
 *
 * <p>{@code medioPreferido} es por donde el comprador pidio el dinero (Ley 2439 de 2024), nulo si
 * no lo dijo. {@code preferenciaRespetada} lo calcula el servidor comparandolo con el medio del
 * reintegro, y no el panel: es la respuesta a "cumplimos o no", y esa no se deja a una comparacion
 * de cadenas en el navegador. Nulo mientras no haya reintegro — todavia no hay nada que comparar.
 */
public record SolicitudRetractoRespuesta(
    String id,
    String pedidoId,
    Instant radicadaEn,
    String radicadaPor,
    String motivo,
    String verdictoAlRadicar,
    String estado,
    Instant productoRecibidoEn,
    Instant limiteDeReintegro,
    String medioPreferido,
    Boolean preferenciaRespetada,
    ReintegroRespuesta reintegro) {}
