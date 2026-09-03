package co.tecnosport.api.domain.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnvioTest {

  private static final UUID PEDIDO_ID = UUID.randomUUID();
  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");

  @Test
  void creaUnEnvioConLosDatosDelDespacho() {
    Envio envio = Envio.crear(PEDIDO_ID, "Servientrega", "SE123456", Dinero.deCop(15_000), AHORA);

    assertEquals(PEDIDO_ID, envio.pedidoId());
    assertEquals("Servientrega", envio.transportadora());
    assertEquals("SE123456", envio.guia());
    assertEquals(Dinero.deCop(15_000), envio.costoEnvio());
    assertEquals(AHORA, envio.despachadoEn());
  }

  @Test
  void unaTransportadoraVaciaSeRechaza() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> Envio.crear(PEDIDO_ID, " ", "SE123456", Dinero.deCop(15_000), AHORA));
  }

  @Test
  void unaGuiaVaciaSeRechaza() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> Envio.crear(PEDIDO_ID, "Servientrega", "", Dinero.deCop(15_000), AHORA));
  }
}
