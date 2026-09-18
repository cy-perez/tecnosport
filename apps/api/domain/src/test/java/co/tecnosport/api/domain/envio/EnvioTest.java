package co.tecnosport.api.domain.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

  /**
   * El rótulo no está garantizado: dos guías de Servientrega emitidas por el mismo camino, una
   * trajo {@code label_url} y la otra no lo trajo nunca, ni con el envío ya entregado
   * (docs/13-skydropx-capacidades.md §6.7). Una guía sin etiqueta es una guía válida.
   */
  @Test
  void unaGuiaEmitidaPuedeVenirSinEtiqueta() {
    GuiaEnvio conRotulo =
        GuiaEnvio.emitida(
            "Servientrega",
            "servientrega",
            "2269401762",
            Dinero.deCop(8_200),
            "https://sb-pro.skydropx.com/s/s?id=ABC");
    GuiaEnvio sinRotulo =
        GuiaEnvio.emitida("Servientrega", "servientrega", "2269401763", Dinero.deCop(8_200), null);

    assertEquals("https://sb-pro.skydropx.com/s/s?id=ABC", conRotulo.urlEtiqueta().orElseThrow());
    assertTrue(sinRotulo.urlEtiqueta().isEmpty());
    assertTrue(sinRotulo.conciliable());
  }

  /** Una guía tecleada en el panel no tiene ni código ni rótulo: se emitió por fuera. */
  @Test
  void unaGuiaTecleadaAManoNoTraeRotuloNiCodigo() {
    GuiaEnvio aMano = GuiaEnvio.crear("Servientrega", "SE123456", Dinero.deCop(15_000));

    assertTrue(aMano.urlEtiqueta().isEmpty());
    assertTrue(aMano.codigoTransportadora().isEmpty());
    assertFalse(aMano.conciliable());
  }

  @Test
  void conciliaElRecaudoConSuComision() {
    Envio envio = conUnaGuia();
    Instant conciliadoEn = AHORA.plusSeconds(3600);

    envio.conciliarRecaudo(ModalidadRecaudo.BANCO, Dinero.deCop(5_000), conciliadoEn);

    assertEquals(Dinero.deCop(5_000), envio.comisionRecaudo().orElseThrow());
    assertEquals(conciliadoEn, envio.recaudoConciliadoEn().orElseThrow());
    assertEquals(ModalidadRecaudo.BANCO, envio.modalidadRecaudo().orElseThrow());
  }

  @Test
  void unEnvioSinConciliarNoTieneComisionNiFecha() {
    Envio envio = conUnaGuia();

    assertTrue(envio.comisionRecaudo().isEmpty());
    assertTrue(envio.recaudoConciliadoEn().isEmpty());
    assertTrue(envio.modalidadRecaudo().isEmpty());
  }

  @Test
  void unRecaudoYaConciliadoNoSePuedeConciliarDeNuevo() {
    Envio envio = conUnaGuia();
    envio.conciliarRecaudo(ModalidadRecaudo.BANCO, Dinero.deCop(5_000), AHORA);

    assertThrows(
        ExcepcionDeDominio.class,
        () -> envio.conciliarRecaudo(ModalidadRecaudo.BANCO, Dinero.deCop(5_000), AHORA));
  }

  /**
   * Los créditos de la plataforma no cobran comisión (docs/13 §3), así que una conciliación a
   * créditos con un número encima está mal en una de las dos cosas: o la modalidad o la cifra. No
   * se guarda a medias.
   */
  @Test
  void elRecaudoACreditosNoAdmiteComision() {
    Envio envio = conUnaGuia();

    assertThrows(
        ExcepcionDeDominio.class,
        () -> envio.conciliarRecaudo(ModalidadRecaudo.CREDITOS, Dinero.deCop(5_000), AHORA));
    assertTrue(envio.recaudoConciliadoEn().isEmpty());
  }

  @Test
  void elRecaudoACreditosSeConciliaConComisionCero() {
    Envio envio = conUnaGuia();

    envio.conciliarRecaudo(ModalidadRecaudo.CREDITOS, Dinero.deCop(0), AHORA);

    assertEquals(ModalidadRecaudo.CREDITOS, envio.modalidadRecaudo().orElseThrow());
    assertEquals(Dinero.deCop(0), envio.comisionRecaudo().orElseThrow());
  }
}
