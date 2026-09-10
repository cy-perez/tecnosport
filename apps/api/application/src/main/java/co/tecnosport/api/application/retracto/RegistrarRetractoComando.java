package co.tecnosport.api.application.retracto;

import co.tecnosport.api.domain.reintegro.MedioReintegro;
import java.util.UUID;

/**
 * {@code motivo} es opcional a propósito: el retracto se ejerce sin justificar (art. 47). Si el
 * comprador dio uno, se guarda; si no, no se le inventa.
 *
 * <p>{@code medioPreferido} también, y por otra razón: la Ley 2439 de 2024 obliga a devolver el
 * dinero por el medio que el comprador prefiera, pero puede no haberlo dicho todavía. Se anota
 * cuando llegue; lo que no se hace es suponerlo.
 */
public record RegistrarRetractoComando(
    UUID pedidoId, String motivo, MedioReintegro medioPreferido, String actor) {

  public RegistrarRetractoComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor que radica no puede estar vacío.");
    }
  }
}
