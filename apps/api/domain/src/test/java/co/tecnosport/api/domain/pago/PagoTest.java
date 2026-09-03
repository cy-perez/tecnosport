package co.tecnosport.api.domain.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.pedido.MetodoPago;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PagoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final ReferenciaPago REFERENCIA = new ReferenciaPago("TS-PED-000123-1");

  private Pago crear() {
    return Pago.crear(
        UUID.randomUUID(), REFERENCIA, MetodoPago.NEQUI, Dinero.deCop(100_000), AHORA);
  }

  @Test
  void crearQuedaPendiente() {
    Pago pago = crear();

    assertEquals(EstadoPago.PENDIENTE, pago.estado());
    assertTrue(pago.eventos().isEmpty());
  }

  @Test
  void aplicarEventoValidoActualizaEstadoYRegistraElEvento() {
    Pago pago = crear();
    Instant recibidoEn = AHORA.plusSeconds(30);

    boolean cambio = pago.aplicarEvento(new EventoPago("evt-1", EstadoPago.APROBADO, recibidoEn));

    assertTrue(cambio);
    assertEquals(EstadoPago.APROBADO, pago.estado());
    assertEquals(1, pago.eventos().size());
    assertEquals(recibidoEn, pago.actualizadoEn());
  }

  @Test
  void aplicarElMismoIdEventoDosVecesEsInofensivo() {
    Pago pago = crear();
    pago.aplicarEvento(new EventoPago("evt-1", EstadoPago.APROBADO, AHORA.plusSeconds(30)));

    boolean segundaVez =
        pago.aplicarEvento(new EventoPago("evt-1", EstadoPago.APROBADO, AHORA.plusSeconds(90)));

    assertFalse(segundaVez);
    assertEquals(1, pago.eventos().size());
    assertEquals(AHORA.plusSeconds(30), pago.actualizadoEn());
  }

  @Test
  void aplicarEventoConTransicionInvalidaLanzaExcepcion() {
    Pago pago = crear();
    pago.aplicarEvento(new EventoPago("evt-1", EstadoPago.APROBADO, AHORA.plusSeconds(30)));

    assertThrows(
        TransicionDePagoInvalidaException.class,
        () ->
            pago.aplicarEvento(
                new EventoPago("evt-2", EstadoPago.RECHAZADO, AHORA.plusSeconds(60))));
  }

  @Test
  void referenciaVaciaSeRechaza() {
    assertThrows(ReferenciaPagoInvalidaException.class, () -> new ReferenciaPago("  "));
  }

  @Test
  void eventoConIdVacioSeRechaza() {
    assertThrows(ExcepcionDeDominio.class, () -> new EventoPago("", EstadoPago.APROBADO, AHORA));
  }

  @Test
  void unPagoNuevoNoTieneIdDeTransaccionWompi() {
    Pago pago = crear();

    assertTrue(pago.idTransaccionWompi().isEmpty());
  }

  @Test
  void registrarElIdDeTransaccionWompiQuedaDisponible() {
    Pago pago = crear();

    pago.registrarIdTransaccionWompi("1234-1610641025-49201");

    assertEquals("1234-1610641025-49201", pago.idTransaccionWompi().orElseThrow());
  }

  @Test
  void registrarElMismoIdDosVecesEsInofensivo() {
    Pago pago = crear();
    pago.registrarIdTransaccionWompi("1234-1610641025-49201");

    pago.registrarIdTransaccionWompi("1234-1610641025-49201");

    assertEquals("1234-1610641025-49201", pago.idTransaccionWompi().orElseThrow());
  }

  @Test
  void registrarUnIdDistintoAlYaRegistradoSeRechaza() {
    Pago pago = crear();
    pago.registrarIdTransaccionWompi("1234-1610641025-49201");

    assertThrows(
        ExcepcionDeDominio.class, () -> pago.registrarIdTransaccionWompi("otro-id-distinto"));
  }

  @Test
  void registrarUnIdVacioSeRechaza() {
    Pago pago = crear();

    assertThrows(ExcepcionDeDominio.class, () -> pago.registrarIdTransaccionWompi("  "));
  }
}
