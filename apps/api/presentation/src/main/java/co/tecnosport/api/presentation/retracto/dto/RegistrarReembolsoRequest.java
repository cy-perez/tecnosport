package co.tecnosport.api.presentation.retracto.dto;

import java.math.BigDecimal;

public record RegistrarReembolsoRequest(BigDecimal monto, String medio, String comprobante) {}
