package co.tecnosport.api.presentation.retracto.dto;

import java.math.BigDecimal;

/**
 * {@code medio} es por donde salio la plata; {@code medioPreferido} es por donde la pidieron, y se
 * manda solo si la solicitud no lo traia ya. Son dos datos y no uno: la Ley 2439 de 2024 exige que
 * coincidan, y sin los dos no se puede demostrar que coincidieron.
 */
public record RegistrarReintegroRequest(
    BigDecimal monto, String medio, String medioPreferido, String comprobante) {}
