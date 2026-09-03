package co.tecnosport.api.application.pedido;

import java.util.UUID;

public record VerificarContraentregaComando(UUID pedidoId, String actor, String motivo) {

  public VerificarContraentregaComando {
    if (motivo == null || motivo.isBlank()) {
      throw new IllegalArgumentException("El motivo de la verificación no puede estar vacío.");
    }
  }
}
