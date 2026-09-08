package co.tecnosport.api.application.carrito;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.carrito.Carrito;
import java.util.Objects;

public final class ActualizarCantidadDeLinea {

  private final RepositorioCarrito repositorioCarrito;
  private final Reloj reloj;

  public ActualizarCantidadDeLinea(RepositorioCarrito repositorioCarrito, Reloj reloj) {
    this.repositorioCarrito =
        Objects.requireNonNull(repositorioCarrito, "El repositorio de carritos no puede ser nulo.");
    this.reloj = Objects.requireNonNull(reloj, "El reloj no puede ser nulo.");
  }

  public Carrito ejecutar(ActualizarCantidadDeLineaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Carrito carrito =
        repositorioCarrito
            .buscarPorId(comando.carritoId())
            .orElseThrow(() -> new CarritoNoEncontradoException(comando.carritoId()));
    carrito.actualizarCantidad(comando.lineaId(), comando.cantidad(), reloj.ahora());
    repositorioCarrito.guardar(carrito);
    return carrito;
  }
}
