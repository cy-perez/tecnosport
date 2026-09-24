package co.tecnosport.api.domain.inventario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventarioTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");

  private Inventario conExistencia(int cantidad) {
    Inventario inventario = Inventario.crear(UUID.randomUUID());
    inventario.registrarEntrada(cantidad, "siembra de prueba", AHORA);
    return inventario;
  }

  /**
   * Un agregado reconstruido no tiene movimientos nuevos, y los que se le agreguen después sí lo
   * son. Es lo que permite que el repositorio escriba solo lo que falta por escribir en vez de
   * reescribir el histórico entero.
   */
  @Test
  void loQueLlegaPorElConstructorNoEsNuevoYLoQueSeAgregaDespuesSi() {
    Inventario reconstruido =
        new Inventario(
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of(
                new MovimientoInventario(
                    UUID.randomUUID(),
                    TipoMovimientoInventario.ENTRADA,
                    5,
                    AHORA,
                    null,
                    null,
                    "siembra")));

    assertEquals(1, reconstruido.movimientos().size());
    assertTrue(reconstruido.movimientosNuevos().isEmpty());

    reconstruido.registrarAjuste(-1, "conteo", AHORA);

    assertEquals(2, reconstruido.movimientos().size());
    assertEquals(1, reconstruido.movimientosNuevos().size());
    assertEquals(TipoMovimientoInventario.AJUSTE, reconstruido.movimientosNuevos().get(0).tipo());
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

  /**
   * El mensaje de una excepción de dominio viaja tal cual en el {@code detail} del 409 —{@code
   * ManejadorDeErrores} publica {@code getMessage()}—, así que este decía cuántas unidades quedan y
   * de qué variante. Con eso, pedir un pedido de 9.999 unidades era una forma de leer el inventario
   * exacto, y repetirlo, de saber cuándo otro comprador acaba de reservar una. {@code docs/02}
   * decidió que el catálogo público publica un booleano por variante, no un número.
   */
  @Test
  void elMensajeDeExistenciaInsuficienteNoPublicaElSaldoNiLaVariante() {
    Inventario inventario = conExistencia(2);

    ExistenciaInsuficienteException excepcion =
        assertThrows(
            ExistenciaInsuficienteException.class,
            () -> inventario.reservar(3, Duration.ofMinutes(30), AHORA));

    assertFalse(excepcion.getMessage().contains("2"), "el mensaje delata el saldo disponible");
    assertFalse(
        excepcion.getMessage().contains(inventario.varianteId().toString()),
        "el mensaje delata el identificador interno de la variante");
    // Los datos siguen disponibles para quien los necesite de este lado.
    assertEquals(2, excepcion.disponible());
    assertEquals(3, excepcion.pedida());
    assertEquals(inventario.varianteId(), excepcion.varianteId());
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
