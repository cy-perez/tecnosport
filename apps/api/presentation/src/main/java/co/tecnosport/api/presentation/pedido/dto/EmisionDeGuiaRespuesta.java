package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.domain.envio.EmisionDeGuia;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que el panel sabe de una emisión.
 *
 * <p><strong>No lleva los identificadores de la plataforma</strong>, y no es descuido: son de
 * Skydropx y no le dicen nada a quien despacha, que lo que necesita saber es si ya puede imprimir.
 * Viven en la base y en el registro, que es donde se buscan el día que haya que rastrear un cobro.
 *
 * <p>{@code cuantosEnvios} sí va, porque es lo que explica por qué un pedido de dos variantes va a
 * traer dos guías y dos cobros (adr/0031).
 */
public record EmisionDeGuiaRespuesta(
    UUID id,
    String estado,
    String transportadora,
    int cuantosEnvios,
    String detalle,
    Instant solicitadaEn,
    Instant resueltaEn) {

  public static EmisionDeGuiaRespuesta de(EmisionDeGuia emision) {
    return new EmisionDeGuiaRespuesta(
        emision.id(),
        emision.estado().name(),
        emision.transportadora(),
        emision.enviosEnPlataforma().size(),
        emision.detalle().orElse(null),
        emision.solicitadaEn(),
        emision.resueltaEn().orElse(null));
  }
}
