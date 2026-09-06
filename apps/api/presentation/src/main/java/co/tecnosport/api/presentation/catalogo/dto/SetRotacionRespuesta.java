package co.tecnosport.api.presentation.catalogo.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El set como lo ve el panel: incluye el estado y cuántos fotogramas se prometieron, que es lo que
 * el asistente necesita para saber qué le falta. Distinto de {@code RotacionRespuesta}, que es lo
 * que ve el visitante en la ficha y solo existe cuando el set está publicado.
 */
public record SetRotacionRespuesta(
    UUID id,
    UUID productoId,
    int fotogramasPrometidos,
    String estado,
    String capturadoPor,
    Instant capturadoEn,
    String dispositivo,
    String versionAsistente,
    List<ImagenRotacionRespuesta> imagenes) {}
