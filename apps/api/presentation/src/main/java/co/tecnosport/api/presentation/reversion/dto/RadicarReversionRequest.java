package co.tecnosport.api.presentation.reversion.dto;

import java.time.Instant;

/**
 * {@code fechaDelHecho} es cuando el comprador tuvo noticia de lo ocurrido; {@code recibidaEn},
 * cuando llego su mensaje. Son dos relojes distintos —el suyo y el nuestro— y por eso son dos
 * campos.
 */
public record RadicarReversionRequest(
    String causal, Instant fechaDelHecho, Instant recibidaEn, String descripcion) {}
