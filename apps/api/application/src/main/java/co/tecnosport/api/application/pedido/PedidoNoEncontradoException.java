package co.tecnosport.api.application.pedido;

import java.util.UUID;

public final class PedidoNoEncontradoException extends RuntimeException {

  public PedidoNoEncontradoException(UUID pedidoId) {
    super("No existe un pedido con id " + pedidoId + ".");
  }
}
