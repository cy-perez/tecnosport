package co.tecnosport.api.presentation.retracto.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code motivo} viaja aunque aqui siempre valga {@code RETRACTO}: la constancia es la misma para
 * los cinco caminos que devuelven dinero, y omitir el campo en esta pantalla obligaria a un DTO
 * distinto en cada una.
 */
public record ReintegroRespuesta(
    String id,
    String motivo,
    BigDecimal monto,
    String medio,
    String comprobante,
    Instant registradoEn,
    String registradoPor) {}
