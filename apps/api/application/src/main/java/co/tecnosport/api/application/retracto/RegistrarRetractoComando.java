package co.tecnosport.api.application.retracto;

import java.util.UUID;

/**
 * {@code motivo} es opcional a propósito: el retracto se ejerce sin justificar (art. 47). Si el
 * comprador dio uno, se guarda; si no, no se le inventa.
 */
public record RegistrarRetractoComando(UUID pedidoId, String motivo, String actor) {

  public RegistrarRetractoComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor que radica no puede estar vacío.");
    }
  }
}
