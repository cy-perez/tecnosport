package co.tecnosport.api.application.carrito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.carrito.Carrito;
import co.tecnosport.api.domain.compartido.CantidadPorLineaExcedidaException;
import co.tecnosport.api.domain.compartido.TopeDeUnidadesPorLinea;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgregarLineaAlCarritoTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");

  @Test
  void agregaUnaLineaNuevaAlCarrito() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();
    Carrito carrito = Carrito.crear(null, AHORA);
    repositorio.guardar(carrito);
    UUID varianteId = UUID.randomUUID();

    Carrito actualizado =
        new AgregarLineaAlCarrito(
                repositorio,
                new RelojFalso(AHORA),
                new co.tecnosport.api.domain.compartido.TopeDeUnidadesPorLinea(5))
            .ejecutar(new AgregarLineaAlCarritoComando(carrito.id(), varianteId, 2));

    assertEquals(1, actualizado.lineas().size());
    assertEquals(2, actualizado.lineas().get(0).cantidad());
  }

  @Test
  void agregarLaMismaVarianteDosVecesSumaCantidad() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();
    Carrito carrito = Carrito.crear(null, AHORA);
    repositorio.guardar(carrito);
    UUID varianteId = UUID.randomUUID();
    AgregarLineaAlCarrito caso =
        new AgregarLineaAlCarrito(
            repositorio,
            new RelojFalso(AHORA),
            new co.tecnosport.api.domain.compartido.TopeDeUnidadesPorLinea(5));

    caso.ejecutar(new AgregarLineaAlCarritoComando(carrito.id(), varianteId, 2));
    Carrito actualizado =
        caso.ejecutar(new AgregarLineaAlCarritoComando(carrito.id(), varianteId, 3));

    assertEquals(1, actualizado.lineas().size());
    assertEquals(5, actualizado.lineas().get(0).cantidad());
  }

  /**
   * Agregar suma a la línea que ya está, así que el tope se mide sobre la suma: tres y tres no
   * caben en cinco, aunque cada pedido por separado sí.
   */
  @Test
  void agregarPorEncimaDelTopeSeRechazaYLaLineaQuedaComoEstaba() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();
    Carrito carrito = Carrito.crear(null, AHORA);
    repositorio.guardar(carrito);
    UUID varianteId = UUID.randomUUID();
    AgregarLineaAlCarrito caso =
        new AgregarLineaAlCarrito(
            repositorio, new RelojFalso(AHORA), new TopeDeUnidadesPorLinea(5));
    caso.ejecutar(new AgregarLineaAlCarritoComando(carrito.id(), varianteId, 3));

    assertThrows(
        CantidadPorLineaExcedidaException.class,
        () -> caso.ejecutar(new AgregarLineaAlCarritoComando(carrito.id(), varianteId, 3)));
    assertEquals(3, carrito.lineas().get(0).cantidad());
  }

  @Test
  void unCarritoInexistenteLanzaCarritoNoEncontrado() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();

    assertThrows(
        CarritoNoEncontradoException.class,
        () ->
            new AgregarLineaAlCarrito(
                    repositorio,
                    new RelojFalso(AHORA),
                    new co.tecnosport.api.domain.compartido.TopeDeUnidadesPorLinea(5))
                .ejecutar(
                    new AgregarLineaAlCarritoComando(UUID.randomUUID(), UUID.randomUUID(), 1)));
  }
}
