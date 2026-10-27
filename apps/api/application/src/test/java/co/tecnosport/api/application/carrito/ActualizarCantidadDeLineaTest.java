package co.tecnosport.api.application.carrito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.carrito.Carrito;
import co.tecnosport.api.domain.carrito.LineaCarritoNoEncontradaException;
import co.tecnosport.api.domain.compartido.CantidadPorLineaExcedidaException;
import co.tecnosport.api.domain.compartido.TopeDeUnidadesPorLinea;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActualizarCantidadDeLineaTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");

  @Test
  void actualizaLaCantidadDeUnaLineaExistente() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA);
    repositorio.guardar(carrito);
    UUID lineaId = carrito.lineas().get(0).id();

    Carrito actualizado =
        new ActualizarCantidadDeLinea(
                repositorio,
                new RelojFalso(AHORA),
                new co.tecnosport.api.domain.compartido.TopeDeUnidadesPorLinea(5))
            .ejecutar(new ActualizarCantidadDeLineaComando(carrito.id(), lineaId, 4));

    assertEquals(4, actualizado.lineas().get(0).cantidad());
  }

  /** El tope de unidades por línea: más de cinco no se guarda, y la línea queda como estaba. */
  @Test
  void unaCantidadPorEncimaDelTopeSeRechaza() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA);
    repositorio.guardar(carrito);
    UUID lineaId = carrito.lineas().get(0).id();
    ActualizarCantidadDeLinea caso =
        new ActualizarCantidadDeLinea(
            repositorio, new RelojFalso(AHORA), new TopeDeUnidadesPorLinea(5));

    assertThrows(
        CantidadPorLineaExcedidaException.class,
        () -> caso.ejecutar(new ActualizarCantidadDeLineaComando(carrito.id(), lineaId, 6)));
    assertEquals(1, carrito.lineas().get(0).cantidad());
    assertEquals(
        5,
        caso.ejecutar(new ActualizarCantidadDeLineaComando(carrito.id(), lineaId, 5))
            .lineas()
            .get(0)
            .cantidad());
  }

  @Test
  void unCarritoInexistenteLanzaCarritoNoEncontrado() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();

    assertThrows(
        CarritoNoEncontradoException.class,
        () ->
            new ActualizarCantidadDeLinea(
                    repositorio,
                    new RelojFalso(AHORA),
                    new co.tecnosport.api.domain.compartido.TopeDeUnidadesPorLinea(5))
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
            new ActualizarCantidadDeLinea(
                    repositorio,
                    new RelojFalso(AHORA),
                    new co.tecnosport.api.domain.compartido.TopeDeUnidadesPorLinea(5))
                .ejecutar(
                    new ActualizarCantidadDeLineaComando(carrito.id(), UUID.randomUUID(), 1)));
  }
}
