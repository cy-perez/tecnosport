package co.tecnosport.api.presentation.atencion.dto;

import java.time.Instant;

public record RespuestaRespuesta(Instant respondidaEn, String respondidaPor, String resumen) {}
