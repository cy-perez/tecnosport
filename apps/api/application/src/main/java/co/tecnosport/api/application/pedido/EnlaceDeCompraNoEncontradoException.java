package co.tecnosport.api.application.pedido;

import java.util.UUID;

/** El panel pidió un enlace de compra que no existe. */
public final class EnlaceDeCompraNoEncontradoException extends RuntimeException {

  public EnlaceDeCompraNoEncontradoException(UUID enlaceId) {
    super("No existe un enlace de compra con id " + enlaceId + ".");
  }
}
