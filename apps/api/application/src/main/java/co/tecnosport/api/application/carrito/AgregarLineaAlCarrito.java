package co.tecnosport.api.application.carrito;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.carrito.Carrito;
import co.tecnosport.api.domain.carrito.LineaCarrito;
import co.tecnosport.api.domain.compartido.TopeDeUnidadesPorLinea;
import java.util.Objects;

/**
 * No valida que {@code varianteId} exista en el catálogo ni que haya existencia: agregar al carrito
 * no reserva ni exige stock (docs/00-producto.md — eso ocurre al iniciar el pago, Fase 3). Un
 * varianteId inválido simplemente no va a poder pagarse.
 *
 * <p>Lo que sí exige es el tope de unidades por línea ({@link TopeDeUnidadesPorLinea}): el pedido
 * lo va a exigir igual, y dejar que el carrito lo pase solo movería el rechazo al último paso.
 */
public final class AgregarLineaAlCarrito {

  private final RepositorioCarrito repositorioCarrito;
  private final Reloj reloj;
  private final TopeDeUnidadesPorLinea tope;

  public AgregarLineaAlCarrito(
      RepositorioCarrito repositorioCarrito, Reloj reloj, TopeDeUnidadesPorLinea tope) {
    this.tope = Objects.requireNonNull(tope, "El tope de unidades por línea no puede ser nulo.");
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
    if (comando.enlaceCompra() == null || comando.enlaceCompra().isBlank()) {
      // Agregar suma a la línea que ya tenga esa variante: lo que se mide es el resultado.
      int yaEnElCarrito =
          carrito.lineas().stream()
              .filter(linea -> linea.varianteId().equals(comando.varianteId()))
              .mapToInt(LineaCarrito::cantidad)
              .sum();
      tope.exigir(yaEnElCarrito + comando.cantidad());
      carrito.agregarLinea(comando.varianteId(), comando.cantidad(), reloj.ahora());
    } else {
      // Sin comprobar aquí si el enlace sigue vigente: el carrito tampoco comprueba existencias, y
      // quien decide es el pedido, que lo bloquea y lo consume (ADR-0086).
      carrito.agregarConEnlace(comando.varianteId(), comando.enlaceCompra(), reloj.ahora());
    }
    repositorioCarrito.guardar(carrito);
    return carrito;
  }
}
