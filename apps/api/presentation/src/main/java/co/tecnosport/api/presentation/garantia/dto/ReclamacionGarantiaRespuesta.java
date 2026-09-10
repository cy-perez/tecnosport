package co.tecnosport.api.presentation.garantia.dto;

import java.time.Instant;

/**
 * {@code vigencia} puede ser {@code INDETERMINADA} y la pantalla tiene que decirlo con esas
 * palabras: sin el termino de esa categoria cargado —hoy, el de los celulares— el sistema no puede
 * afirmar que la garantia vencio, y pintarlo como vencida seria negar un derecho.
 *
 * <p>{@code finDelTermino} viaja nulo en ese mismo caso, y no como una fecha inventada.
 */
public record ReclamacionGarantiaRespuesta(
    String id,
    String solicitudId,
    String pedidoId,
    String varianteId,
    Instant entregadoEn,
    Instant radicadaEn,
    Integer mesesDeTermino,
    Instant finDelTermino,
    String vigencia,
    String descripcionDelFallo,
    String estado,
    String desenlace,
    Instant resueltaEn,
    String resueltaPor,
    String reintegroId) {}
