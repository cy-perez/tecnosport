package co.tecnosport.api.domain.inventario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventarioTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");

  private Inventario conExistencia(int cantidad) {
    Inventario inventario = Inventario.crear(UUID.randomUUID());
    inventario.registrarEntrada(cantidad, "siembra de prueba", AHORA);
    return inventario;
  }

  @Test
  void reservarBajaElSaldoDisponibleSinBajarElSaldoTotal() {
    Inventario inventario = conExistencia(5);

    inventario.reservar(2, Duration.ofMinutes(30), AHORA);

    assertEquals(5, inventario.saldoTotal());
    assertEquals(3, inventario.saldoDisponible(AHORA));
  }

  @Test
  void reservarMasDeLoDisponibleLanzaExistenciaInsuficiente() {
    Inventario inventario = conExistencia(2);

    assertThrows(
        ExistenciaInsuficienteException.class,
        () -> inventario.reservar(3, Duration.ofMinutes(30), AHORA));
  }

  @Test
  void reservarCantidadCeroONegativaEsInvalido() {
    Inventario inventario = conExistencia(5);

    assertThrows(
        co.tecnosport.api.domain.compartido.ExcepcionDeDominio.class,
        () -> inventario.reservar(0, Duration.ofMinutes(30), AHORA));
  }

  @Test
  void confirmarConvierteLaReservaEnSalidaYBajaElSaldoTotal() {
    Inventario inventario = conExistencia(5);
    MovimientoInventario reserva = inventario.reservar(2, Duration.ofMinutes(30), AHORA);

    inventario.confirmar(reserva.id(), AHORA.plusSeconds(60));

    assertEquals(3, inventario.saldoTotal());
    assertEquals(3, inventario.saldoDisponible(AHORA.plusSeconds(60)));
  }

  @Test
  void confirmarDosVecesLaMismaReservaLanzaReservaYaProcesada() {
    Inventario inventario = conExistencia(5);
    MovimientoInventario reserva = inventario.reservar(2, Duration.ofMinutes(30), AHORA);
    inventario.confirmar(reserva.id(), AHORA);

    assertThrows(
        ReservaYaProcesadaException.class, () -> inventario.confirmar(reserva.id(), AHORA));
  }

  @Test
  void confirmarUnIdInexistenteLanzaReservaNoEncontrada() {
    Inventario inventario = conExistencia(5);

    assertThrows(
        ReservaNoEncontradaException.class, () -> inventario.confirmar(UUID.randomUUID(), AHORA));
  }

  @Test
  void confirmarUnaReservaVencidaLanzaReservaYaProcesada() {
    Inventario inventario = conExistencia(5);
    MovimientoInventario reserva = inventario.reservar(2, Duration.ofMinutes(30), AHORA);

    assertThrows(
        ReservaYaProcesadaException.class,
        () -> inventario.confirmar(reserva.id(), AHORA.plus(Duration.ofMinutes(31))));
  }

  @Test
  void liberarDevuelveLaCantidadAlSaldoDisponible() {
    Inventario inventario = conExistencia(5);
    MovimientoInventario reserva = inventario.reservar(2, Duration.ofMinutes(30), AHORA);

    inventario.liberar(reserva.id(), "el cliente canceló", AHORA.plusSeconds(60));

    assertEquals(5, inventario.saldoTotal());
    assertEquals(5, inventario.saldoDisponible(AHORA.plusSeconds(60)));
  }

  @Test
  void liberarUnaReservaYaVencidaFunciona() {
    Inventario inventario = conExistencia(5);
    MovimientoInventario reserva = inventario.reservar(2, Duration.ofMinutes(30), AHORA);
    Instant despuesDeVencer = AHORA.plus(Duration.ofMinutes(31));

    inventario.liberar(reserva.id(), "venció", despuesDeVencer);

    assertEquals(5, inventario.saldoDisponible(despuesDeVencer));
  }

  @Test
  void liberarDosVecesLaMismaReservaLanzaReservaYaProcesada() {
    Inventario inventario = conExistencia(5);
    MovimientoInventario reserva = inventario.reservar(2, Duration.ofMinutes(30), AHORA);
    inventario.liberar(reserva.id(), "canceló", AHORA);

    assertThrows(
        ReservaYaProcesadaException.class,
        () -> inventario.liberar(reserva.id(), "otra vez", AHORA));
  }

  @Test
  void unaReservaVencidaNoCuentaEnElDisponibleAunqueNadieLaHayaLiberado() {
    Inventario inventario = conExistencia(5);
    inventario.reservar(5, Duration.ofMinutes(30), AHORA);

    assertEquals(0, inventario.saldoDisponible(AHORA));
    assertEquals(5, inventario.saldoDisponible(AHORA.plus(Duration.ofMinutes(31))));
  }

  @Test
  void unaReservaSinVigenciaNuncaVence() {
    Inventario inventario = conExistencia(3);

    inventario.reservar(3, null, AHORA);

    assertEquals(0, inventario.saldoDisponible(AHORA.plus(Duration.ofDays(365))));
  }

  @Test
  void registrarEntradaSubeElSaldoTotalYElDisponible() {
    Inventario inventario = Inventario.crear(UUID.randomUUID());

    inventario.registrarEntrada(10, "compra a proveedor", AHORA);

    assertEquals(10, inventario.saldoTotal());
    assertEquals(10, inventario.saldoDisponible(AHORA));
  }

  @Test
  void registrarAjustePositivoYNegativoMuevenElSaldoTotal() {
    Inventario inventario = conExistencia(5);

    inventario.registrarAjuste(2, "conteo físico encontró más unidades", AHORA);
    assertEquals(7, inventario.saldoTotal());

    inventario.registrarAjuste(-3, "unidades dañadas", AHORA);
    assertEquals(4, inventario.saldoTotal());
  }

  @Test
  void registrarAjusteQueDejariaElSaldoNegativoSeRechaza() {
    Inventario inventario = conExistencia(2);

    assertThrows(
        co.tecnosport.api.domain.compartido.ExcepcionDeDominio.class,
        () -> inventario.registrarAjuste(-5, "pérdida", AHORA));
  }

  @Test
  void devolverUnaVentaConfirmadaSumaUnaEntrada() {
    Inventario inventario = conExistencia(5);
    MovimientoInventario reserva = inventario.reservar(2, Duration.ofMinutes(30), AHORA);
    inventario.confirmar(reserva.id(), AHORA.plusSeconds(60));

    inventario.devolver(reserva.id(), "retracto", AHORA.plusSeconds(120));

    assertEquals(5, inventario.saldoTotal());
    assertEquals(5, inventario.saldoDisponible(AHORA.plusSeconds(120)));
  }

  @Test
  void devolverUnaReservaAbiertaLaLiberaEnVezDeContarLaUnidadDosVeces() {
    // El caso del contraentrega: la reserva no vence ni se confirma nunca, así que la unidad
    // jamás salió del saldo total. Una entrada aquí dejaría seis donde hay cinco.
    Inventario inventario = conExistencia(5);
    MovimientoInventario reserva = inventario.reservar(2, null, AHORA);

    inventario.devolver(reserva.id(), "retracto", AHORA.plusSeconds(120));

    assertEquals(5, inventario.saldoTotal());
    assertEquals(5, inventario.saldoDisponible(AHORA.plusSeconds(120)));
  }

  @Test
  void noSeDevuelveDosVecesLaMismaReservaAbierta() {
    Inventario inventario = conExistencia(5);
    MovimientoInventario reserva = inventario.reservar(2, null, AHORA);
    inventario.devolver(reserva.id(), "retracto", AHORA.plusSeconds(120));

    assertThrows(
        ReservaYaProcesadaException.class,
        () -> inventario.devolver(reserva.id(), "retracto", AHORA.plusSeconds(180)));
  }

  @Test
  void noSeDevuelveDosVecesLaMismaVentaConfirmada() {
    Inventario inventario = conExistencia(5);
    MovimientoInventario reserva = inventario.reservar(2, Duration.ofMinutes(30), AHORA);
    inventario.confirmar(reserva.id(), AHORA.plusSeconds(60));
    inventario.devolver(reserva.id(), "retracto", AHORA.plusSeconds(120));

    assertThrows(
        ReservaYaProcesadaException.class,
        () -> inventario.devolver(reserva.id(), "retracto", AHORA.plusSeconds(180)));
    assertEquals(5, inventario.saldoTotal());
  }

  @Test
  void devolverUnaReservaQueNoExisteFalla() {
    Inventario inventario = conExistencia(5);

    assertThrows(
        ReservaNoEncontradaException.class,
        () -> inventario.devolver(UUID.randomUUID(), "retracto", AHORA));
  }
}
