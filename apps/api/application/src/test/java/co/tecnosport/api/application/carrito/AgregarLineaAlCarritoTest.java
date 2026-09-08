package co.tecnosport.api.application.carrito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.carrito.Carrito;
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
        new AgregarLineaAlCarrito(repositorio, new RelojFalso(AHORA))
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
    AgregarLineaAlCarrito caso = new AgregarLineaAlCarrito(repositorio, new RelojFalso(AHORA));

    caso.ejecutar(new AgregarLineaAlCarritoComando(carrito.id(), varianteId, 2));
    Carrito actualizado =
        caso.ejecutar(new AgregarLineaAlCarritoComando(carrito.id(), varianteId, 3));

    assertEquals(1, actualizado.lineas().size());
    assertEquals(5, actualizado.lineas().get(0).cantidad());
  }

  @Test
  void unCarritoInexistenteLanzaCarritoNoEncontrado() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();

    assertThrows(
        CarritoNoEncontradoException.class,
        () ->
            new AgregarLineaAlCarrito(repositorio, new RelojFalso(AHORA))
                .ejecutar(
                    new AgregarLineaAlCarritoComando(UUID.randomUUID(), UUID.randomUUID(), 1)));
  }
}
