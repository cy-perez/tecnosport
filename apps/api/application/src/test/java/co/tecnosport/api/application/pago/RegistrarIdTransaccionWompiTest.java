package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  private PasarelaDePagosFalsa pasarela;

  private RegistrarIdTransaccionWompi crear() {
    pagos = new RepositorioPagosFalso();
    pasarela = new PasarelaDePagosFalsa();
    return new RegistrarIdTransaccionWompi(pagos, pasarela);
  }

  private Pago pago() {
    Pago pago =
        Pago.crear(UUID.randomUUID(), REFERENCIA, MetodoPago.WOMPI, Dinero.deCop(100_000), AHORA);
    pagos.guardar(pago);
    return pago;
  }

  @Test
  void registraElIdEnElPagoEncontradoPorReferencia() {
    RegistrarIdTransaccionWompi caso = crear();
    pasarela.conTransaccionDe("1234-1610641025-49201", "PENDING", pago());

    caso.ejecutar(
        new RegistrarIdTransaccionWompiComando(REFERENCIA.valor(), "1234-1610641025-49201"));

    Pago pagoActualizado = pagos.buscarPorReferencia(REFERENCIA).orElseThrow();
    assertEquals("1234-1610641025-49201", pagoActualizado.idTransaccionPasarela().orElseThrow());
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

  /**
   * El ataque que esto cierra: el endpoint es anónimo y la referencia predecible. Un id ajeno —o
   * basura— estampado primero dejaba el pago sin poder conciliarse nunca, porque se queda con el
   * primero que recibe.
   */
  @Test
  void unIdDeOtraTransaccionNoSeRegistra() {
    RegistrarIdTransaccionWompi caso = crear();
    pago();
    pasarela.conTransaccionAjena("ajena-1", "APPROVED", "TS-2026-000099-1", Dinero.deCop(20_000));

    assertThrows(
        TransaccionDeOtroPagoException.class,
        () -> caso.ejecutar(new RegistrarIdTransaccionWompiComando(REFERENCIA.valor(), "ajena-1")));
    assertTrue(
        pagos.buscarPorReferencia(REFERENCIA).orElseThrow().idTransaccionPasarela().isEmpty());
  }

  @Test
  void unIdQueLaPasarelaNoConoceNoSeRegistra() {
    RegistrarIdTransaccionWompi caso = crear();
    pago();

    assertThrows(
        TransaccionDeOtroPagoException.class,
        () -> caso.ejecutar(new RegistrarIdTransaccionWompiComando(REFERENCIA.valor(), "basura")));
    assertTrue(
        pagos.buscarPorReferencia(REFERENCIA).orElseThrow().idTransaccionPasarela().isEmpty());
  }

  /** Repetir el mismo id no vuelve a preguntarle a la pasarela: ya se verificó. */
  @Test
  void elMismoIdOtraVezNoConsultaDeNuevo() {
    RegistrarIdTransaccionWompi caso = crear();
    Pago pago = pago();
    pago.registrarIdTransaccionPasarela("ya-verificado");
    pagos.guardar(pago);

    caso.ejecutar(new RegistrarIdTransaccionWompiComando(REFERENCIA.valor(), "ya-verificado"));

    assertEquals(
        "ya-verificado",
        pagos.buscarPorReferencia(REFERENCIA).orElseThrow().idTransaccionPasarela().orElseThrow());
  }
}
