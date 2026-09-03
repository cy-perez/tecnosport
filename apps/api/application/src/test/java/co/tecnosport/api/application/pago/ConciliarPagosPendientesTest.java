package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
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

class ConciliarPagosPendientesTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration ANTIGUEDAD_MINIMA = Duration.ofMinutes(15);
  private static final CorreoElectronico CORREO = new CorreoElectronico("cliente@tecnosport.co");
  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;
  private RepositorioPagosFalso pagos;
  private PasarelaDePagosFalsa pasarela;
  private int secuencial = 0;

  private ConciliarPagosPendientes crear() {
    pedidos = new RepositorioPedidosFalso();
    pagos = new RepositorioPagosFalso();
    pasarela = new PasarelaDePagosFalsa();
    return new ConciliarPagosPendientes(
        pagos, pedidos, pasarela, new RelojFalso(AHORA), ANTIGUEDAD_MINIMA);
  }

  private Pedido pedidoNuevo() {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, ++secuencial),
            null,
            CORREO,
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(100_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp")),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.NEQUI,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.conPedido(pedido);
    return pedido;
  }

  private Pago pagoPendiente(Pedido pedido, String idTransaccionWompi, Instant creadoEn) {
    Pago pago =
        Pago.crear(
            pedido.id(),
            new ReferenciaPago(pedido.numeroPedido().valor() + "-1"),
            pedido.metodoPago(),
            pedido.total(),
            creadoEn);
    if (idTransaccionWompi != null) {
      pago.registrarIdTransaccionWompi(idTransaccionWompi);
    }
    pagos.guardar(pago);
    return pago;
  }

  @Test
  void conciliaUnPagoAprobadoYPropagaAlPedido() {
    ConciliarPagosPendientes caso = crear();
    Pedido pedido = pedidoNuevo();
    pagoPendiente(pedido, "wompi-tx-1", AHORA.minus(Duration.ofMinutes(20)));
    pasarela.conEstadoDeTransaccion("wompi-tx-1", "APPROVED");

    ResultadoConciliacion resultado = caso.ejecutar();

    assertEquals(new ResultadoConciliacion(1, 1, 0), resultado);
    assertEquals(EstadoPedido.PAGADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void unPagoMasNuevoQueElUmbralNoSeRevisa() {
    ConciliarPagosPendientes caso = crear();
    Pedido pedido = pedidoNuevo();
    pagoPendiente(pedido, "wompi-tx-1", AHORA.minus(Duration.ofMinutes(5)));
    pasarela.conEstadoDeTransaccion("wompi-tx-1", "APPROVED");

    ResultadoConciliacion resultado = caso.ejecutar();

    assertEquals(new ResultadoConciliacion(0, 0, 0), resultado);
    assertEquals(
        EstadoPedido.PAGO_PENDIENTE, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void unPagoSinIdTransaccionWompiNoSeRevisa() {
    ConciliarPagosPendientes caso = crear();
    Pedido pedido = pedidoNuevo();
    pagoPendiente(pedido, null, AHORA.minus(Duration.ofMinutes(20)));

    ResultadoConciliacion resultado = caso.ejecutar();

    assertEquals(new ResultadoConciliacion(0, 0, 0), resultado);
  }

  @Test
  void unaConsultaSinRespuestaCuentaComoSinNovedad() {
    ConciliarPagosPendientes caso = crear();
    Pedido pedido = pedidoNuevo();
    pagoPendiente(pedido, "wompi-tx-1", AHORA.minus(Duration.ofMinutes(20)));
    // Sin conEstadoDeTransaccion: la pasarela responde Optional.empty().

    ResultadoConciliacion resultado = caso.ejecutar();

    assertEquals(new ResultadoConciliacion(1, 0, 1), resultado);
  }

  @Test
  void unEstadoTodaviaPendienteEnWompiCuentaComoSinNovedad() {
    ConciliarPagosPendientes caso = crear();
    Pedido pedido = pedidoNuevo();
    pagoPendiente(pedido, "wompi-tx-1", AHORA.minus(Duration.ofMinutes(20)));
    pasarela.conEstadoDeTransaccion("wompi-tx-1", "PENDING");

    ResultadoConciliacion resultado = caso.ejecutar();

    assertEquals(new ResultadoConciliacion(1, 0, 1), resultado);
    assertEquals(
        EstadoPedido.PAGO_PENDIENTE, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void unEstadoVoidedCuentaComoSinNovedad() {
    ConciliarPagosPendientes caso = crear();
    Pedido pedido = pedidoNuevo();
    pagoPendiente(pedido, "wompi-tx-1", AHORA.minus(Duration.ofMinutes(20)));
    pasarela.conEstadoDeTransaccion("wompi-tx-1", "VOIDED");

    ResultadoConciliacion resultado = caso.ejecutar();

    assertEquals(new ResultadoConciliacion(1, 0, 1), resultado);
  }
}
