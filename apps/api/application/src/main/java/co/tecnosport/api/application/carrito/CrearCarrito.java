package co.tecnosport.api.application.carrito;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.carrito.Carrito;
import java.util.Objects;

public final class CrearCarrito {

  private final RepositorioCarrito repositorioCarrito;
  private final Reloj reloj;

  public CrearCarrito(RepositorioCarrito repositorioCarrito, Reloj reloj) {
    this.repositorioCarrito =
        Objects.requireNonNull(repositorioCarrito, "El repositorio de carritos no puede ser nulo.");
    this.reloj = Objects.requireNonNull(reloj, "El reloj no puede ser nulo.");
  }

  public Carrito ejecutar(CrearCarritoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Carrito carrito = Carrito.crear(comando.usuarioId(), reloj.ahora());
    repositorioCarrito.guardar(carrito);
    return carrito;
  }
}
