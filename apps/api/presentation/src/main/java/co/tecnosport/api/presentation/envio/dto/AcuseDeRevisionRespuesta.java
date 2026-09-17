package co.tecnosport.api.presentation.envio.dto;

import java.time.Instant;

/**
 * El acuse recién guardado. Se devuelve entero, con su instante, porque es lo que la pantalla
 * necesita para decir "revisado por ti hace un momento" sin volver a preguntar ni fiarse del reloj
 * del navegador — el instante lo pone el servidor.
 */
public record AcuseDeRevisionRespuesta(
    String id, String tipo, String referencia, Instant revisadoEn, String actor, String nota) {}
