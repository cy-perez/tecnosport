package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrearIntentoDePagoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final CorreoElectronico CORREO = new CorreoElectronico("cliente@tecnosport.co");
  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;
  private RepositorioPagosFalso pagos;

  private CrearIntentoDePago crear() {
    pedidos = new RepositorioPedidosFalso();
    pagos = new RepositorioPagosFalso();
    return new CrearIntentoDePago(
        pedidos, pagos, new PasarelaDePagosFalsa(), new RelojFalso(AHORA));
  }

  private LineaPedido linea() {
    return new LineaPedido(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        2,
        Dinero.deCop(50_000),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.webp");
  }

  private Pedido pedidoConMetodo(MetodoPago metodoPago, int secuencial) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, secuencial),
            null,
            CORREO,
            List.of(linea()),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            metodoPago,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.conPedido(pedido);
    return pedido;
  }

  @Test
  void creaUnPagoPendienteYDevuelveLaFirma() {
    CrearIntentoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI, 1);

    IntentoDePago intento = caso.ejecutar(new CrearIntentoDePagoComando(pedido.id()));

    assertEquals("TS-2026-000001-1", intento.referencia().valor());
    assertEquals(pedido.total(), intento.monto());
    assertTrue(intento.firmaIntegridad().contains(intento.referencia().valor()));

    Pago pagoGuardado = pagos.buscarPorReferencia(intento.referencia()).orElseThrow();
    assertEquals(EstadoPago.PENDIENTE, pagoGuardado.estado());
    assertEquals(pedido.id(), pagoGuardado.pedidoId());
  }

  @Test
  void unSegundoIntentoIncrementaElNumeroDeLaReferencia() {
    CrearIntentoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI, 1);

    caso.ejecutar(new CrearIntentoDePagoComando(pedido.id()));
    IntentoDePago segundo = caso.ejecutar(new CrearIntentoDePagoComando(pedido.id()));

    assertEquals("TS-2026-000001-2", segundo.referencia().valor());
    assertEquals(2, pagos.buscarPorPedidoId(pedido.id()).size());
  }

  @Test
  void pedidoInexistenteLanzaPedidoNoEncontrado() {
    CrearIntentoDePago caso = crear();
    pedidos = new RepositorioPedidosFalso();

    assertThrows(
        co.tecnosport.api.application.pedido.PedidoNoEncontradoException.class,
        () -> caso.ejecutar(new CrearIntentoDePagoComando(UUID.randomUUID())));
  }

  @Test
  void pedidoQueNoEstaEnPagoPendienteSeRechaza() {
    CrearIntentoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA, 1);

    assertThrows(
        PedidoNoEstaEnPagoPendienteException.class,
        () -> caso.ejecutar(new CrearIntentoDePagoComando(pedido.id())));
  }

  @Test
  void metodoDePagoQueNoVaPorWompiSeRechaza() {
    CrearIntentoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.TRANSFERENCIA_MANUAL, 1);

    assertThrows(
        MetodoDePagoNoSoportadoPorWompiException.class,
        () -> caso.ejecutar(new CrearIntentoDePagoComando(pedido.id())));
  }
}
