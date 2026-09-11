package co.tecnosport.api.presentation.reversion.dto;

import java.time.Instant;

/**
 * {@code verdictoAlRadicar} puede ser {@code INDETERMINADO} y la pantalla tiene que decirlo asi:
 * sin el calendario de festivos cargado no se puede afirmar que el plazo del comprador vencio.
 *
 * <p>{@code gestion} es lo que demuestra que se facilito el tramite, que es lo que los terminos
 * publicados prometen. Viaja al panel, no al comprador.
 */
public record SolicitudReversionRespuesta(
    String id,
    String solicitudId,
    String pedidoId,
    String causal,
    Instant fechaDeNoticia,
    Instant radicadaEn,
    String verdictoAlRadicar,
    String estado,
    Instant gestionadaEn,
    String gestionadaPor,
    String gestion,
    String desenlace,
    Instant resueltaEn,
    String reintegroId) {}
