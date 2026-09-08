package co.tecnosport.api.application.carrito;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.carrito.Carrito;
import java.util.Objects;

/**
 * No valida que {@code varianteId} exista en el catálogo ni que haya existencia: agregar al carrito
 * no reserva ni exige stock (docs/00-producto.md — eso ocurre al iniciar el pago, Fase 3). Un
 * varianteId inválido simplemente no va a poder pagarse.
 */
public final class AgregarLineaAlCarrito {

  private final RepositorioCarrito repositorioCarrito;
  private final Reloj reloj;

  public AgregarLineaAlCarrito(RepositorioCarrito repositorioCarrito, Reloj reloj) {
    this.repositorioCarrito =
        Objects.requireNonNull(repositorioCarrito, "El repositorio de carritos no puede ser nulo.");
    this.reloj = Objects.requireNonNull(reloj, "El reloj no puede ser nulo.");
  }

  public Carrito ejecutar(AgregarLineaAlCarritoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Carrito carrito =
        repositorioCarrito
            .buscarPorId(comando.carritoId())
            .orElseThrow(() -> new CarritoNoEncontradoException(comando.carritoId()));
    carrito.agregarLinea(comando.varianteId(), comando.cantidad(), reloj.ahora());
    repositorioCarrito.guardar(carrito);
    return carrito;
  }
}
