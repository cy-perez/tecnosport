package co.tecnosport.api.presentation.reversion.dto;

import java.math.BigDecimal;

public record ResolverReversionRequest(
    String desenlace,
    String resumenParaElComprador,
    BigDecimal monto,
    String medio,
    String comprobante) {}
