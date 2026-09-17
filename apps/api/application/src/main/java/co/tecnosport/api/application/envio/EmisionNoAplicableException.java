package co.tecnosport.api.application.envio;

import java.util.UUID;

/**
 * Este pedido no admite que se le emita una guía ahora mismo. No es un fallo del proveedor ni algo
 * que se arregle reintentando: es el pedido el que está en otro sitio.
 */
public class EmisionNoAplicableException extends RuntimeException {

  private final UUID pedidoId;

  public EmisionNoAplicableException(UUID pedidoId, String porque) {
    super("No se puede emitir la guía del pedido " + pedidoId + ": " + porque + ".");
    this.pedidoId = pedidoId;
  }

  public UUID pedidoId() {
    return pedidoId;
  }
}
