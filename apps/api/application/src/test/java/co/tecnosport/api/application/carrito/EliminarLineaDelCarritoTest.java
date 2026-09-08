package co.tecnosport.api.application.carrito;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.carrito.Carrito;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EliminarLineaDelCarritoTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");

  @Test
  void quitaLaLineaDelCarrito() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA);
    repositorio.guardar(carrito);
    UUID lineaId = carrito.lineas().get(0).id();

    Carrito actualizado =
        new EliminarLineaDelCarrito(repositorio, new RelojFalso(AHORA))
            .ejecutar(new EliminarLineaDelCarritoComando(carrito.id(), lineaId));

    assertTrue(actualizado.lineas().isEmpty());
  }

  @Test
  void unCarritoInexistenteLanzaCarritoNoEncontrado() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();

    assertThrows(
        CarritoNoEncontradoException.class,
        () ->
            new EliminarLineaDelCarrito(repositorio, new RelojFalso(AHORA))
                .ejecutar(
                    new EliminarLineaDelCarritoComando(UUID.randomUUID(), UUID.randomUUID())));
  }
}
