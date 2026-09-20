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
  void unPagoNuevoNoTieneIdDeTransaccionDeLaPasarela() {
    Pago pago = crear();

    assertTrue(pago.idTransaccionPasarela().isEmpty());
  }

  @Test
  void registrarElIdDeTransaccionDeLaPasarelaQuedaDisponible() {
    Pago pago = crear();

    pago.registrarIdTransaccionPasarela("1234-1610641025-49201");

    assertEquals("1234-1610641025-49201", pago.idTransaccionPasarela().orElseThrow());
  }

  @Test
  void registrarElMismoIdDosVecesEsInofensivo() {
    Pago pago = crear();
    pago.registrarIdTransaccionPasarela("1234-1610641025-49201");

    pago.registrarIdTransaccionPasarela("1234-1610641025-49201");

    assertEquals("1234-1610641025-49201", pago.idTransaccionPasarela().orElseThrow());
  }

  @Test
  void registrarUnIdDistintoAlYaRegistradoSeRechaza() {
    Pago pago = crear();
    pago.registrarIdTransaccionPasarela("1234-1610641025-49201");

    assertThrows(
        ExcepcionDeDominio.class, () -> pago.registrarIdTransaccionPasarela("otro-id-distinto"));
  }

  @Test
  void registrarUnIdVacioSeRechaza() {
    Pago pago = crear();

    assertThrows(ExcepcionDeDominio.class, () -> pago.registrarIdTransaccionPasarela("  "));
  }

  @Test
  void unPagoRecienNacidoNoSabeConQueSeCobro() {
    assertTrue(crear().medioReportadoPorLaPasarela().isEmpty());
  }

  /**
   * El corazón del asunto: el pago guarda lo que el comprador eligió <b>y</b> lo que la pasarela
   * cobró, sin que el segundo borre al primero. El Web Checkout hospedado no recibe la elección del
   * comprador —Wompi pinta su propia lista— así que estos dos pueden no coincidir, y si el método
   * elegido se machacara con el reportado no quedaría ninguna prueba de que el sitio ofreció una
   * cosa y cobró otra.
   */
  @Test
  void elMedioReportadoNoPisaElMetodoQueEligioElComprador() {
    Pago pago = crear();

    pago.registrarMedioReportadoPorLaPasarela("CARD");

    assertEquals("CARD", pago.medioReportadoPorLaPasarela().orElseThrow());
    assertEquals(MetodoPago.NEQUI, pago.metodoPago());
  }

  /** Un evento que no trae el medio no borra lo que ya se sabía: "no viene" no es "no fue". */
  @Test
  void unMedioNuloOVacioNoBorraElQueYaSeConocia() {
    Pago pago = crear();
    pago.registrarMedioReportadoPorLaPasarela("PSE");

    pago.registrarMedioReportadoPorLaPasarela(null);
    pago.registrarMedioReportadoPorLaPasarela("   ");

    assertEquals("PSE", pago.medioReportadoPorLaPasarela().orElseThrow());
  }

  /** Lo reporta la pasarela: este agregado no lo discute, se queda con lo último que dijo. */
  @Test
  void seQuedaConElUltimoMedioQueReportoLaPasarela() {
    Pago pago = crear();
    pago.registrarMedioReportadoPorLaPasarela("NEQUI");

    pago.registrarMedioReportadoPorLaPasarela("CARD");

    assertEquals("CARD", pago.medioReportadoPorLaPasarela().orElseThrow());
  }
}
