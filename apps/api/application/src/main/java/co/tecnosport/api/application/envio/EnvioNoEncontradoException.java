package co.tecnosport.api.application.envio;

import java.util.UUID;

public final class EnvioNoEncontradoException extends RuntimeException {

  public EnvioNoEncontradoException(UUID pedidoId) {
    super("No existe un envío para el pedido " + pedidoId + ".");
  }
}
