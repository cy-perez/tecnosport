package co.tecnosport.api.presentation.atencion.dto;

import java.time.Instant;

/**
 * {@code limiteDeRespuesta} y {@code verdicto} los calcula el servidor con el calendario y los
 * plazos configurados, no la pantalla: son plazos legales y no pueden depender del reloj ni de la
 * zona horaria del navegador de quien mire.
 *
 * <p>{@code verdicto} puede ser {@code INDETERMINADO} y la pantalla tiene que decirlo con esas
 * palabras: sin el calendario de festivos cargado, el sistema no puede afirmar que un plazo vencio.
 *
 * <p>Las dos fechas viajan juntas a proposito. {@code recibidaEn} es de la que cuelga el plazo;
 * {@code radicadaEn} es cuando alguien la registro, y la distancia entre las dos es lo unico que
 * despues explica por que nadie se entero a tiempo.
 */
public record SolicitudAtencionRespuesta(
    String id,
    String numeroRadicado,
    String tipo,
    String correo,
    String pedidoId,
    Instant recibidaEn,
    Instant radicadaEn,
    String radicadaPor,
    String asunto,
    String estado,
    Instant limiteDeRespuesta,
    String verdicto,
    ProrrogaRespuesta prorroga,
    RespuestaRespuesta respuesta) {}
