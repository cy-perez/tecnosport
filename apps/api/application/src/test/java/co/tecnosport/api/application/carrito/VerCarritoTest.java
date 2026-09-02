package co.tecnosport.api.application.carrito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.carrito.Carrito;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerCarritoTest {

  @Test
  void devuelveElCarritoExistente() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();
    Carrito carrito = Carrito.crear(null, Instant.parse("2026-09-02T12:00:00Z"));
    repositorio.guardar(carrito);

    Carrito encontrado = new VerCarrito(repositorio).ejecutar(new VerCarritoComando(carrito.id()));

    assertEquals(carrito.id(), encontrado.id());
  }

  @Test
  void unIdInexistenteLanzaCarritoNoEncontrado() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();

    assertThrows(
        CarritoNoEncontradoException.class,
        () -> new VerCarrito(repositorio).ejecutar(new VerCarritoComando(UUID.randomUUID())));
  }
}
