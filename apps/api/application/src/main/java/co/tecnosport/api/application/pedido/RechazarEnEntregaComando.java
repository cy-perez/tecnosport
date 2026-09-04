package co.tecnosport.api.application.pedido;

import java.util.UUID;

public record RechazarEnEntregaComando(UUID pedidoId, String motivo, String actor) {

  public RechazarEnEntregaComando {
    if (motivo == null || motivo.isBlank()) {
      throw new IllegalArgumentException("El motivo del rechazo no puede estar vacío.");
    }
  }
}
