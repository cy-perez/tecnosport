package co.tecnosport.api.presentation.envio.dto;

import java.time.Instant;
import java.util.List;

/**
 * Cómo quedó la emisión después de resolverla. Se devuelve el estado y no un simple 204 porque los
 * dos veredictos llevan a sitios distintos —{@code FALLIDA} libera el pedido, {@code EN_CURSO} deja
 * a la tarea releyendo— y la pantalla tiene que poder decir cuál de los dos pasó.
 */
public record EmisionResueltaRespuesta(
    String emisionId,
    String estado,
    String detalle,
    List<String> enviosEnPlataforma,
    Instant resueltaEn) {}
