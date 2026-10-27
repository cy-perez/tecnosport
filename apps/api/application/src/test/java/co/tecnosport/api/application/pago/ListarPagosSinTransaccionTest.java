package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** La alerta de pagos de Wompi que nadie concilia, y su salida: marcarlos revisados. */
class ListarPagosSinTransaccionTest {

  private static final Instant AHORA = Instant.parse("2026-10-27T12:00:00Z");
  private static final Duration UMBRAL = Duration.ofHours(12);

  private final RepositorioPagosFalso pagos = new RepositorioPagosFalso();
  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private int secuencial;

  private ListarPagosSinTransaccion listar() {
    return new ListarPagosSinTransaccion(pagos, pedidos, new RelojFalso(AHORA), UMBRAL);
  }

  private Pago pago(MetodoPago metodo, Instant creadoEn) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, ++secuencial),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta",
                    1,
                    Dinero.deCop(50_000),
                    BigDecimal.ZERO,
                    null,
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            metodo,
            "cliente@tecnosport.co",
            creadoEn);
    pedidos.conPedido(pedido);
    Pago pago =
        Pago.crear(
            pedido.id(),
            new ReferenciaPago(pedido.numeroPedido().valor() + "-1"),
            metodo,
            Dinero.deCop(50_000),
            creadoEn);
    pagos.guardar(pago);
    return pago;
  }

  @Test
  void muestraElPendienteSinIdMasViejoQueElUmbralConSuPedido() {
    Pago viejo = pago(MetodoPago.WOMPI, AHORA.minus(Duration.ofHours(13)));

    PagosSinTransaccion alerta = listar().ejecutar();

    assertEquals(UMBRAL, alerta.umbral());
    assertEquals(1, alerta.pagos().size());
    assertEquals(viejo.id(), alerta.pagos().get(0).pago().id());
    assertEquals(viejo.pedidoId(), alerta.pagos().get(0).pedido().id());
  }

  /** Dentro del umbral todavía puede llegar el webhook o volver el navegador. */
  @Test
  void elRecienteNoSaleTodavia() {
    pago(MetodoPago.WOMPI, AHORA.minus(Duration.ofHours(11)));

    assertTrue(listar().ejecutar().pagos().isEmpty());
  }

  /** Con id, la conciliación ya lo consulta: no es de esta alerta. */
  @Test
  void elQueTieneIdNoSale() {
    Pago conId = pago(MetodoPago.WOMPI, AHORA.minus(Duration.ofHours(13)));
    conId.registrarIdTransaccionPasarela("1234-1610641025-49201");
    pagos.guardar(conId);

    assertTrue(listar().ejecutar().pagos().isEmpty());
  }

  @Test
  void elResueltoNoSale() {
    Pago resuelto = pago(MetodoPago.WOMPI, AHORA.minus(Duration.ofHours(13)));
    resuelto.aplicarEvento(new EventoPago("evt-1", EstadoPago.RECHAZADO, AHORA));
    pagos.guardar(resuelto);

    assertTrue(listar().ejecutar().pagos().isEmpty());
  }

  /** A Sistecrédito el id se lo da la creación del intento; uno sin id no llegó a existir allá. */
  @Test
  void unoDeSistecreditoNoSale() {
    pago(MetodoPago.SISTECREDITO, AHORA.minus(Duration.ofHours(13)));

    assertTrue(listar().ejecutar().pagos().isEmpty());
  }

  @Test
  void marcadoComoRevisadoDejaDeSalir() {
    Pago viejo = pago(MetodoPago.WOMPI, AHORA.minus(Duration.ofHours(13)));

    new MarcarPagoSinTransaccionRevisado(pagos, new RelojFalso(AHORA))
        .ejecutar(viejo.id(), "admin:1");

    assertTrue(listar().ejecutar().pagos().isEmpty());
  }

  /** La lista del panel estaba vieja: el pago ya tiene su transacción. No se marca nada. */
  @Test
  void noSeMarcaUnPagoQueYaTieneSuTransaccion() {
    Pago conId = pago(MetodoPago.WOMPI, AHORA.minus(Duration.ofHours(13)));
    conId.registrarIdTransaccionPasarela("1234-1610641025-49201");
    pagos.guardar(conId);

    assertThrows(
        PagoConTransaccionOResueltoException.class,
        () ->
            new MarcarPagoSinTransaccionRevisado(pagos, new RelojFalso(AHORA))
                .ejecutar(conId.id(), "admin:1"));
  }

  @Test
  void unUmbralNoPositivoSeRechazaAlConstruir() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ListarPagosSinTransaccion(pagos, pedidos, new RelojFalso(AHORA), Duration.ZERO));
  }
}
