package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.MetodoPago;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistrarIdTransaccionWompiTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final ReferenciaPago REFERENCIA = new ReferenciaPago("TS-2026-000001-1");

  private RepositorioPagosFalso pagos;

  private RegistrarIdTransaccionWompi crear() {
    pagos = new RepositorioPagosFalso();
    return new RegistrarIdTransaccionWompi(pagos);
  }

  @Test
  void registraElIdEnElPagoEncontradoPorReferencia() {
    RegistrarIdTransaccionWompi caso = crear();
    Pago pago =
        Pago.crear(UUID.randomUUID(), REFERENCIA, MetodoPago.NEQUI, Dinero.deCop(100_000), AHORA);
    pagos.guardar(pago);

    caso.ejecutar(
        new RegistrarIdTransaccionWompiComando(REFERENCIA.valor(), "1234-1610641025-49201"));

    Pago pagoActualizado = pagos.buscarPorReferencia(REFERENCIA).orElseThrow();
    assertEquals("1234-1610641025-49201", pagoActualizado.idTransaccionWompi().orElseThrow());
  }

  @Test
  void referenciaInexistenteLanzaPagoNoEncontrado() {
    RegistrarIdTransaccionWompi caso = crear();

    assertThrows(
        PagoNoEncontradoException.class,
        () ->
            caso.ejecutar(
                new RegistrarIdTransaccionWompiComando("no-existe", "1234-1610641025-49201")));
  }
}
