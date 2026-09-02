package co.tecnosport.api.application.carrito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.carrito.Carrito;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrearCarritoTest {

  @Test
  void creaYGuardaUnCarritoAnonimo() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();
    RelojFalso reloj = new RelojFalso(Instant.parse("2026-09-02T12:00:00Z"));

    Carrito carrito = new CrearCarrito(repositorio, reloj).ejecutar(new CrearCarritoComando(null));

    assertTrue(carrito.usuarioId().isEmpty());
    assertEquals(carrito.id(), repositorio.buscarPorId(carrito.id()).orElseThrow().id());
  }

  @Test
  void creaUnCarritoDeUsuario() {
    RepositorioCarritoFalso repositorio = new RepositorioCarritoFalso();
    RelojFalso reloj = new RelojFalso(Instant.parse("2026-09-02T12:00:00Z"));
    UUID usuarioId = UUID.randomUUID();

    Carrito carrito =
        new CrearCarrito(repositorio, reloj).ejecutar(new CrearCarritoComando(usuarioId));

    assertEquals(usuarioId, carrito.usuarioId().orElseThrow());
  }
}
