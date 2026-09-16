package co.tecnosport.api.domain.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnvioTest {

  private static final UUID PEDIDO_ID = UUID.randomUUID();
  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");

  private static Envio conUnaGuia() {
    return Envio.crear(
        PEDIDO_ID,
        List.of(GuiaEnvio.crear("Servientrega", "SE123456", Dinero.deCop(15_000))),
        AHORA);
  }

  @Test
  void creaUnEnvioConLosDatosDelDespacho() {
    Envio envio = conUnaGuia();

    assertEquals(PEDIDO_ID, envio.pedidoId());
    assertEquals(1, envio.guias().size());
    assertEquals("Servientrega", envio.guias().getFirst().transportadora());
    assertEquals("SE123456", envio.guias().getFirst().numero());
    assertEquals(Dinero.deCop(15_000), envio.costoEnvio());
    assertEquals(AHORA, envio.despachadoEn());
  }

  /**
   * El caso que trajo adr/0031: ninguna transportadora colombiana admite multipaquete, así que un
   * pedido de dos variantes sale en dos guías con dos cobros.
   */
  @Test
  void elCostoDelDespachoEsLaSumaDeLasGuias() {
    Envio envio =
        Envio.crear(
            PEDIDO_ID,
            List.of(
                GuiaEnvio.crear("Servientrega", "SE123456", Dinero.deCop(15_000)),
                GuiaEnvio.crear("Coordinadora", "CO987", Dinero.deCop(5_991))),
            AHORA);

    assertEquals(Dinero.deCop(20_991), envio.costoEnvio());
  }

  @Test
  void unDespachoSinGuiasNoEsUnDespacho() {
    assertThrows(ExcepcionDeDominio.class, () -> Envio.crear(PEDIDO_ID, List.of(), AHORA));
  }

  /** La base lo impide con un único global; el dominio no tiene por qué esperar a la base. */
  @Test
  void dosGuiasConElMismoNumeroSeRechazan() {
    List<GuiaEnvio> repetidas =
        List.of(
            GuiaEnvio.crear("Servientrega", "SE123456", Dinero.deCop(15_000)),
            GuiaEnvio.crear("Coordinadora", "SE123456", Dinero.deCop(5_991)));

    assertThrows(ExcepcionDeDominio.class, () -> Envio.crear(PEDIDO_ID, repetidas, AHORA));
  }

  @Test
  void laGuiaSeEncuentraPorSuNumeroYLasAjenasNo() {
    Envio envio = conUnaGuia();

    assertEquals("Servientrega", envio.guiaDe("SE123456").orElseThrow().transportadora());
    assertTrue(envio.guiaDe("NO-ES-MIA").isEmpty());
  }

  @Test
  void unaTransportadoraVaciaSeRechaza() {
    assertThrows(
        ExcepcionDeDominio.class, () -> GuiaEnvio.crear(" ", "SE123456", Dinero.deCop(15_000)));
  }

  @Test
  void unaGuiaVaciaSeRechaza() {
    assertThrows(
        ExcepcionDeDominio.class, () -> GuiaEnvio.crear("Servientrega", "", Dinero.deCop(15_000)));
  }

  @Test
  void conciliaElRecaudoConSuComision() {
    Envio envio = conUnaGuia();
    Instant conciliadoEn = AHORA.plusSeconds(3600);

    envio.conciliarRecaudo(Dinero.deCop(5_000), conciliadoEn);

    assertEquals(Dinero.deCop(5_000), envio.comisionRecaudo().orElseThrow());
    assertEquals(conciliadoEn, envio.recaudoConciliadoEn().orElseThrow());
  }

  @Test
  void unEnvioSinConciliarNoTieneComisionNiFecha() {
    Envio envio = conUnaGuia();

    assertTrue(envio.comisionRecaudo().isEmpty());
    assertTrue(envio.recaudoConciliadoEn().isEmpty());
  }

  @Test
  void unRecaudoYaConciliadoNoSePuedeConciliarDeNuevo() {
    Envio envio = conUnaGuia();
    envio.conciliarRecaudo(Dinero.deCop(5_000), AHORA);

    assertThrows(
        ExcepcionDeDominio.class, () -> envio.conciliarRecaudo(Dinero.deCop(5_000), AHORA));
  }
}
