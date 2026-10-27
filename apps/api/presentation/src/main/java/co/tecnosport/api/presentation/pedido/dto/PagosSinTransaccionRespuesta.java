package co.tecnosport.api.presentation.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * La alerta con su umbral: el panel dice "más de {@code umbralHoras} horas" con el número que el
 * servidor aplicó, que sale de la configuración y no de un texto.
 */
public record PagosSinTransaccionRespuesta(
    @Schema(requiredMode = RequiredMode.REQUIRED) long umbralHoras,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<PagoSinTransaccionRespuesta> pagos) {}
