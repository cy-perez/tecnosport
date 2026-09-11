package co.tecnosport.api.presentation.reversion.dto;

import java.time.Instant;

/**
 * {@code fechaDeNoticia} es cuando el comprador tuvo noticia de lo ocurrido; {@code recibidaEn},
 * cuando llego su mensaje. Son dos relojes distintos —el suyo y el nuestro— y por eso son dos
 * campos.
 */
public record RadicarReversionRequest(
    String causal, Instant fechaDeNoticia, Instant recibidaEn, String descripcion) {}
