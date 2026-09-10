package co.tecnosport.api.application.retracto;

import java.util.UUID;

public record RecibirProductoDevueltoComando(UUID solicitudId, String actor) {

  public RecibirProductoDevueltoComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
