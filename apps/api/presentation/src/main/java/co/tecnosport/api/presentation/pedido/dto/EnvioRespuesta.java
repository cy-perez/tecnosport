package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.time.Instant;

/**
 * Solo va en la respuesta cuando el pedido ya se despachó — antes de eso no existe {@code Envio}
 * (docs/02-modelo-datos.md). {@code comisionRecaudo} y {@code recaudoConciliadoEn} quedan nulos
 * hasta conciliar el recaudo de contraentrega.
 */
public record EnvioRespuesta(
    String transportadora,
    String guia,
    DineroRespuesta costoEnvio,
    Instant despachadoEn,
    DineroRespuesta comisionRecaudo,
    Instant recaudoConciliadoEn) {}
