package co.tecnosport.api.application.inventario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.inventario.Inventario;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Lo que la vitrina va a decirle a quien compra. Cada caso de aquí es una forma distinta de que un
 * producto parezca comprable sin serlo, que es el defecto del que nace adr/0050.
 */
class DisponibilidadDeVariantesTest {

  private static final Instant AHORA = Instant.parse("2026-09-20T15:00:00Z");

  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final DisponibilidadDeVariantes disponibilidad =
      new DisponibilidadDeVariantes(inventarios, new RelojFalso(AHORA));

  @Test
  void unLibroConSaldoLibreEstaDisponible() {
    UUID varianteId = UUID.randomUUID();
    inventarios.con(libroCon(varianteId, 3));

    assertTrue(disponibilidad.de(List.of(varianteId)).hay(varianteId));
  }

  /** La última unidad reservada por un pedido en vuelo no se puede vender otra vez. */
  @Test
  void unaReservaVigenteEscondeLaUnidad() {
    UUID varianteId = UUID.randomUUID();
    Inventario libro = libroCon(varianteId, 1);
    libro.reservar(1, Duration.ofMinutes(30), AHORA);
    inventarios.con(libro);

    assertFalse(disponibilidad.de(List.of(varianteId)).hay(varianteId));
  }

  /**
   * Y al vencer vuelve sola, sin que nadie escriba nada. Es la razón por la que esto se calcula al
   * leer: ninguna columna se entera del paso del tiempo.
   */
  @Test
  void unaReservaVencidaDevuelveLaUnidad() {
    UUID varianteId = UUID.randomUUID();
    Inventario libro = libroCon(varianteId, 1);
    libro.reservar(1, Duration.ofMinutes(30), AHORA);
    inventarios.con(libro);

    assertFalse(disponibilidad.de(List.of(varianteId)).hay(varianteId), "la reserva sigue vigente");
    assertTrue(
        new DisponibilidadDeVariantes(inventarios, new RelojFalso(AHORA.plus(Duration.ofHours(1))))
            .de(List.of(varianteId))
            .hay(varianteId),
        "una hora después, la reserva de treinta minutos ya no retiene nada");
  }

  @Test
  void unLibroSinSaldoNoEstaDisponible() {
    UUID varianteId = UUID.randomUUID();
    inventarios.con(libroCon(varianteId, 0));

    assertFalse(disponibilidad.de(List.of(varianteId)).hay(varianteId));
  }

  /** Una variante sin libro es una variante agotada, no una excepción. */
  @Test
  void unaVarianteSinLibroNoEstaDisponible() {
    UUID varianteId = UUID.randomUUID();

    assertFalse(disponibilidad.de(List.of(varianteId)).hay(varianteId));
  }

  @Test
  void sinVariantesNoPreguntaNada() {
    assertTrue(disponibilidad.de(List.of()).ids().isEmpty());
  }

  /** Lo que una página pide no puede contaminarse con el saldo de la variante de al lado. */
  @Test
  void cadaVarianteRespondePorSiMisma() {
    UUID conSaldo = UUID.randomUUID();
    UUID agotada = UUID.randomUUID();
    inventarios.con(libroCon(conSaldo, 2), libroCon(agotada, 0));

    VariantesDisponibles disponibles = disponibilidad.de(List.of(conSaldo, agotada));

    assertTrue(disponibles.hay(conSaldo));
    assertFalse(disponibles.hay(agotada));
  }

  private static Inventario libroCon(UUID varianteId, int unidades) {
    Inventario libro = Inventario.crear(varianteId);
    if (unidades > 0) {
      libro.registrarEntrada(unidades, "siembra de prueba", AHORA.minus(Duration.ofDays(1)));
    }
    return libro;
  }
}
