package co.tecnosport.api.domain.carrito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Duration;
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

    carrito.agregarLinea(varianteId, 2, AHORA);

    assertEquals(1, carrito.lineas().size());
    assertEquals(2, carrito.lineas().get(0).cantidad());
  }

  @Test
  void agregarUnaVarianteYaPresenteSumaLaCantidadEnVezDeDuplicarLaLinea() {
    Carrito carrito = Carrito.crear(null, AHORA);
    UUID varianteId = UUID.randomUUID();
    carrito.agregarLinea(varianteId, 2, AHORA);

    carrito.agregarLinea(varianteId, 3, AHORA);

    assertEquals(1, carrito.lineas().size());
    assertEquals(5, carrito.lineas().get(0).cantidad());
  }

  @Test
  void agregarConCantidadCeroONegativaEsInvalido() {
    Carrito carrito = Carrito.crear(null, AHORA);

    assertThrows(ExcepcionDeDominio.class, () -> carrito.agregarLinea(UUID.randomUUID(), 0, AHORA));
  }

  @Test
  void actualizarCantidadDeUnaLineaExistente() {
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA);
    UUID idLinea = carrito.lineas().get(0).id();

    carrito.actualizarCantidad(idLinea, 7, AHORA);

    assertEquals(7, carrito.lineas().get(0).cantidad());
  }

  @Test
  void actualizarCantidadAceroONegativaSeRechaza() {
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA);
    UUID idLinea = carrito.lineas().get(0).id();

    assertThrows(ExcepcionDeDominio.class, () -> carrito.actualizarCantidad(idLinea, 0, AHORA));
  }

  @Test
  void actualizarUnaLineaInexistenteLanzaLineaCarritoNoEncontrada() {
    Carrito carrito = Carrito.crear(null, AHORA);

    assertThrows(
        LineaCarritoNoEncontradaException.class,
        () -> carrito.actualizarCantidad(UUID.randomUUID(), 1, AHORA));
  }

  @Test
  void eliminarLineaLaQuitaDelCarrito() {
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA);
    UUID idLinea = carrito.lineas().get(0).id();

    carrito.eliminarLinea(idLinea, AHORA);

    assertTrue(carrito.lineas().isEmpty());
  }

  @Test
  void eliminarUnaLineaInexistenteLanzaLineaCarritoNoEncontrada() {
    Carrito carrito = Carrito.crear(null, AHORA);

    assertThrows(
        LineaCarritoNoEncontradaException.class,
        () -> carrito.eliminarLinea(UUID.randomUUID(), AHORA));
  }

  @Test
  void unCarritoReciénCreadoTieneLaMismaFechaDeCreacionYDeActividad() {
    Carrito carrito = Carrito.crear(null, AHORA);

    assertEquals(AHORA, carrito.creadoEn());
    assertEquals(AHORA, carrito.actualizadoEn());
  }

  /**
   * Lo que hace que la purga no borre carritos vivos: usarlo lo mantiene fresco, aunque se haya
   * creado hace meses. La fecha de creación no se mueve.
   */
  @Test
  void cadaMutacionAdelantaLaFechaDeActividad() {
    Carrito carrito = Carrito.crear(null, AHORA);
    Instant despues = AHORA.plus(Duration.ofDays(20));

    carrito.agregarLinea(UUID.randomUUID(), 1, despues);

    assertEquals(despues, carrito.actualizadoEn());
    assertEquals(AHORA, carrito.creadoEn());
  }

  @Test
  void actualizarCantidadTambienCuentaComoActividad() {
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA);
    UUID idLinea = carrito.lineas().get(0).id();
    Instant despues = AHORA.plus(Duration.ofDays(20));

    carrito.actualizarCantidad(idLinea, 3, despues);

    assertEquals(despues, carrito.actualizadoEn());
  }

  @Test
  void eliminarLineaTambienCuentaComoActividad() {
    Carrito carrito = Carrito.crear(null, AHORA);
    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA);
    UUID idLinea = carrito.lineas().get(0).id();
    Instant despues = AHORA.plus(Duration.ofDays(20));

    carrito.eliminarLinea(idLinea, despues);

    assertEquals(despues, carrito.actualizadoEn());
  }

  /**
   * Un reloj corrido hacia atrás —o dos instancias con relojes desalineados— no puede envejecer un
   * carrito vivo hasta que la purga se lo lleve.
   */
  @Test
  void laFechaDeActividadNuncaRetrocede() {
    Carrito carrito = Carrito.crear(null, AHORA);
    Instant despues = AHORA.plus(Duration.ofDays(20));
    carrito.agregarLinea(UUID.randomUUID(), 1, despues);

    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA);

    assertEquals(despues, carrito.actualizadoEn());
  }

  /** Una mutación que falla no es actividad: el carrito no queda más fresco por un error. */
  @Test
  void unaMutacionInvalidaNoAdelantaLaFechaDeActividad() {
    Carrito carrito = Carrito.crear(null, AHORA);
    Instant despues = AHORA.plus(Duration.ofDays(20));

    assertThrows(
        ExcepcionDeDominio.class, () -> carrito.agregarLinea(UUID.randomUUID(), 0, despues));

    assertEquals(AHORA, carrito.actualizadoEn());
  }
}
