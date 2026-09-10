package co.tecnosport.api.presentation.atencion.dto;

import java.time.Instant;

/** {@code avisadaEn} viaja porque es lo que hace valida la prorroga, no un detalle de auditoria. */
public record ProrrogaRespuesta(
    Instant otorgadaEn, String otorgadaPor, String motivo, Instant avisadaEn) {}
