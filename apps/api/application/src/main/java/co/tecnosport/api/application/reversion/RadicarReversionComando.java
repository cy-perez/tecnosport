package co.tecnosport.api.application.reversion;

import co.tecnosport.api.domain.reversion.CausalReversion;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code fechaDeNoticia} es cuándo el comprador tuvo noticia de lo ocurrido: de ella cuelga el
 * plazo de cinco días hábiles que los términos publicados le imponen a él. {@code recibidaEn} es
 * cuándo llegó su mensaje, y de ella cuelga el plazo de respuesta que nos imponemos nosotros. Son
 * dos relojes distintos y por eso son dos campos.
 */
public record RadicarReversionComando(
    UUID pedidoId,
    CausalReversion causal,
    Instant fechaDeNoticia,
    Instant recibidaEn,
    String descripcion,
    String actor) {

  public RadicarReversionComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
