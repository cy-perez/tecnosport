package co.tecnosport.api.application.reversion;

import java.util.UUID;

public record RegistrarGestionReversionComando(UUID reversionId, String gestion, String actor) {

  public RegistrarGestionReversionComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
