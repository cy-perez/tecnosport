package co.tecnosport.api.application.carrito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.carrito.Carrito;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurgarCarritosVencidosTest {

  private static final Instant AHORA = Instant.parse("2026-09-07T12:00:00Z");
  private static final Duration RETENCION = Duration.ofDays(30);

  private RepositorioCarritoFalso repositorio;

  private PurgarCarritosVencidos crear() {
    repositorio = new RepositorioCarritoFalso();
    return new PurgarCarritosVencidos(repositorio, new RelojFalso(AHORA), RETENCION);
  }

  private Carrito carritoConActividad(Instant actividad) {
    Carrito carrito = Carrito.crear(null, actividad);
    repositorio.guardar(carrito);
    return carrito;
  }

  @Test
  void borraUnCarritoSinActividadDesdeHaceMasDeLaRetencion() {
    PurgarCarritosVencidos caso = crear();
    carritoConActividad(AHORA.minus(Duration.ofDays(31)));

    assertEquals(1, caso.ejecutar());
    assertEquals(0, repositorio.cantidad());
  }

  @Test
  void noBorraUnCarritoConActividadReciente() {
    PurgarCarritosVencidos caso = crear();
    carritoConActividad(AHORA.minus(Duration.ofDays(29)));

    assertEquals(0, caso.ejecutar());
    assertEquals(1, repositorio.cantidad());
  }

  /**
   * El caso que justifica toda la columna de actividad: un carrito creado hace meses pero usado
   * ayer es de un cliente vivo, y borrarlo sería borrarle la compra en curso.
   */
  @Test
  void noBorraUnCarritoViejoQueSeSigueUsando() {
    PurgarCarritosVencidos caso = crear();
    Carrito carrito = carritoConActividad(AHORA.minus(Duration.ofDays(200)));
    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA.minus(Duration.ofDays(1)));
    repositorio.guardar(carrito);

    assertEquals(0, caso.ejecutar());
    assertEquals(1, repositorio.cantidad());
  }

  @Test
  void sinCarritosVencidosNoBorraNada() {
    PurgarCarritosVencidos caso = crear();

    assertEquals(0, caso.ejecutar());
  }

  @Test
  void purgarDosVecesSeguidasEsInofensivo() {
    PurgarCarritosVencidos caso = crear();
    carritoConActividad(AHORA.minus(Duration.ofDays(31)));

    assertEquals(1, caso.ejecutar());
    assertEquals(0, caso.ejecutar());
  }

  /** Con retención cero se borraría el carrito que alguien está usando en este momento. */
  @Test
  void unaRetencionNoPositivaNoSeAcepta() {
    RepositorioCarritoFalso repo = new RepositorioCarritoFalso();

    assertThrows(
        IllegalArgumentException.class,
        () -> new PurgarCarritosVencidos(repo, new RelojFalso(AHORA), Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PurgarCarritosVencidos(repo, new RelojFalso(AHORA), Duration.ofDays(-1)));
  }

  @Test
  void borraSoloLosVencidosYDejaLosVivos() {
    PurgarCarritosVencidos caso = crear();
    carritoConActividad(AHORA.minus(Duration.ofDays(31)));
    carritoConActividad(AHORA.minus(Duration.ofDays(40)));
    carritoConActividad(AHORA.minus(Duration.ofDays(2)));

    assertEquals(2, caso.ejecutar());
    assertEquals(1, repositorio.cantidad());
    assertTrue(repositorio.cantidad() > 0);
  }
}
