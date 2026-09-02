package co.tecnosport.api.application.carrito;

import co.tecnosport.api.domain.carrito.Carrito;
import java.util.Objects;

public final class VerCarrito {

  private final RepositorioCarrito repositorioCarrito;

  public VerCarrito(RepositorioCarrito repositorioCarrito) {
    this.repositorioCarrito =
        Objects.requireNonNull(repositorioCarrito, "El repositorio de carritos no puede ser nulo.");
  }

  public Carrito ejecutar(VerCarritoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    return repositorioCarrito
        .buscarPorId(comando.carritoId())
        .orElseThrow(() -> new CarritoNoEncontradoException(comando.carritoId()));
  }
}
