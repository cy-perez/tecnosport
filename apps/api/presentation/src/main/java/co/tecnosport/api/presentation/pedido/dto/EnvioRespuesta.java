package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.time.Instant;
import java.util.List;

/**
 * Solo va en la respuesta cuando el pedido ya se despachó — antes de eso no existe {@code Envio}
 * (docs/02-modelo-datos.md). {@code comisionRecaudo} y {@code recaudoConciliadoEn} quedan nulos
 * hasta conciliar el recaudo de contraentrega.
 *
 * <p>{@code guias} va en plural desde adr/0031: un pedido de dos variantes sale en dos paquetes.
 * {@code costoEnvio} es la suma de las guías, que es lo que el despacho costó de verdad.
 */
public record EnvioRespuesta(
    List<GuiaRespuesta> guias,
    DineroRespuesta costoEnvio,
    Instant despachadoEn,
    DineroRespuesta comisionRecaudo,
    Instant recaudoConciliadoEn) {}
