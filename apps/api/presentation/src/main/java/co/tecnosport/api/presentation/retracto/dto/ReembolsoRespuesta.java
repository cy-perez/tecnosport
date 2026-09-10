package co.tecnosport.api.presentation.retracto.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ReembolsoRespuesta(
    BigDecimal monto,
    String medio,
    String comprobante,
    Instant registradoEn,
    String registradoPor) {}
