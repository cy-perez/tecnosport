package co.tecnosport.api.application.carrito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.carrito.Carrito;
import co.tecnosport.api.domain.carrito.LineaCarritoNoEncontradaException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActualizarCantidadDeLineaTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");

  @Test
  void actualizaLaCantidadDeUnaLineaExistente() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1);
    repositorio.guardar(carrito);
    UUID lineaId = carrito.lineas().get(0).id();

    Carrito actualizado =
        new ActualizarCantidadDeLinea(repositorio)
            .ejecutar(new ActualizarCantidadDeLineaComando(carrito.id(), lineaId, 9));

    assertEquals(9, actualizado.lineas().get(0).cantidad());
  }

  @Test
  void unCarritoInexistenteLanzaCarritoNoEncontrado() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();

    assertThrows(
        CarritoNoEncontradoException.class,
        () ->
            new ActualizarCantidadDeLinea(repositorio)
                .ejecutar(
                    new ActualizarCantidadDeLineaComando(UUID.randomUUID(), UUID.randomUUID(), 1)));
  }

  @Test
  void unaLineaInexistenteLanzaLineaCarritoNoEncontrada() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();
    Carrito carrito = Carrito.crear(null, AHORA);
    repositorio.guardar(carrito);

    assertThrows(
        LineaCarritoNoEncontradaException.class,
        () ->
            new ActualizarCantidadDeLinea(repositorio)
                .ejecutar(
                    new ActualizarCantidadDeLineaComando(carrito.id(), UUID.randomUUID(), 1)));
  }
}
