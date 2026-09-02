package co.tecnosport.api.domain.carrito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CarritoTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");

  @Test
  void crearAnonimoNoTieneUsuario() {
    Carrito carrito = Carrito.crear(null, AHORA);

    assertTrue(carrito.usuarioId().isEmpty());
    assertTrue(carrito.lineas().isEmpty());
  }

  @Test
  void crearConUsuarioLoConserva() {
    UUID usuarioId = UUID.randomUUID();
    Carrito carrito = Carrito.crear(usuarioId, AHORA);

    assertEquals(usuarioId, carrito.usuarioId().orElseThrow());
  }

  @Test
  void agregarUnaVarianteNuevaCreaUnaLinea() {
    Carrito carrito = Carrito.crear(null, AHORA);
    UUID varianteId = UUID.randomUUID();

    carrito.agregarLinea(varianteId, 2);

    assertEquals(1, carrito.lineas().size());
    assertEquals(2, carrito.lineas().get(0).cantidad());
  }

  @Test
  void agregarUnaVarianteYaPresenteSumaLaCantidadEnVezDeDuplicarLaLinea() {
    Carrito carrito = Carrito.crear(null, AHORA);
    UUID varianteId = UUID.randomUUID();
    carrito.agregarLinea(varianteId, 2);

    carrito.agregarLinea(varianteId, 3);

    assertEquals(1, carrito.lineas().size());
    assertEquals(5, carrito.lineas().get(0).cantidad());
  }

  @Test
  void agregarConCantidadCeroONegativaEsInvalido() {
    Carrito carrito = Carrito.crear(null, AHORA);

    assertThrows(ExcepcionDeDominio.class, () -> carrito.agregarLinea(UUID.randomUUID(), 0));
  }

  @Test
  void actualizarCantidadDeUnaLineaExistente() {
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1);
    UUID idLinea = carrito.lineas().get(0).id();

    carrito.actualizarCantidad(idLinea, 7);

    assertEquals(7, carrito.lineas().get(0).cantidad());
  }

  @Test
  void actualizarCantidadAceroONegativaSeRechaza() {
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1);
    UUID idLinea = carrito.lineas().get(0).id();

    assertThrows(ExcepcionDeDominio.class, () -> carrito.actualizarCantidad(idLinea, 0));
  }

  @Test
  void actualizarUnaLineaInexistenteLanzaLineaCarritoNoEncontrada() {
    Carrito carrito = Carrito.crear(null, AHORA);

    assertThrows(
        LineaCarritoNoEncontradaException.class,
        () -> carrito.actualizarCantidad(UUID.randomUUID(), 1));
  }

  @Test
  void eliminarLineaLaQuitaDelCarrito() {
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1);
    UUID idLinea = carrito.lineas().get(0).id();

    carrito.eliminarLinea(idLinea);

    assertTrue(carrito.lineas().isEmpty());
  }

  @Test
  void eliminarUnaLineaInexistenteLanzaLineaCarritoNoEncontrada() {
    Carrito carrito = Carrito.crear(null, AHORA);

    assertThrows(
        LineaCarritoNoEncontradaException.class, () -> carrito.eliminarLinea(UUID.randomUUID()));
  }
}
