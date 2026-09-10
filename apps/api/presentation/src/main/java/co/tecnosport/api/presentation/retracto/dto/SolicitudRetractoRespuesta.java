package co.tecnosport.api.presentation.retracto.dto;

import java.time.Instant;

/**
 * {@code limiteDeReintegro} lo calcula el servidor y no el panel: es el plazo de quince dias
 * calendario del articulo 47 y no puede depender del reloj ni de la zona horaria del navegador de
 * quien mire la pantalla.
 *
 * <p>{@code verdictoAlRadicar} puede ser {@code INDETERMINADO}, y la pantalla tiene que decirlo
 * asi: sin el calendario de festivos cargado, el sistema no puede afirmar que un plazo vencio.
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
    ReintegroRespuesta reintegro) {}
