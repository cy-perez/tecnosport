package co.tecnosport.api.application.carrito;

import java.util.UUID;

public final class CarritoNoEncontradoException extends RuntimeException {

  public CarritoNoEncontradoException(UUID carritoId) {
    super("No existe un carrito con id " + carritoId + ".");
  }
}
