package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.DocumentoIdentidad;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.TipoDocumento;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
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

class CrearIntentoDePagoSistecreditoTest {

  private static final Instant AHORA = Instant.parse("2026-09-20T12:00:00Z");
  private static final CorreoElectronico CORREO = new CorreoElectronico("cliente@tecnosport.co");
  private static final Direccion DIRECCION_MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");
  private static final DocumentoIdentidad DOCUMENTO =
      new DocumentoIdentidad(TipoDocumento.CC, "1017254896");
  private static final String URL_PAGO = "https://siste.credinet.co/pago/abc";

  private RepositorioPedidosFalso pedidos;
  private RepositorioPagosFalso pagos;
  private PasarelaSistecreditoFalsa pasarela;

  private CrearIntentoDePagoSistecredito crear() {
    return crear(false, null);
  }

  private CrearIntentoDePagoSistecredito crear(boolean sandbox, String estadoSimulado) {
    pedidos = new RepositorioPedidosFalso();
    pagos = new RepositorioPagosFalso();
    pasarela = new PasarelaSistecreditoFalsa();
    pasarela.responder(transaccion("Pending", URL_PAGO, null, null));
    return new CrearIntentoDePagoSistecredito(
        pedidos,
        pagos,
        pasarela,
        new RelojFalso(AHORA),
        "https://tecnosport.co/checkout/sistecredito/retorno",
        "https://api.tecnosport.co/api/v1/pagos/sistecredito/confirmacion",
        sandbox,
        estadoSimulado);
  }

  private static TransaccionSistecredito transaccion(
      String estado, String url, String codigo, String descripcion) {
    return new TransaccionSistecredito(
        "649b4c821b581f96e45b5696", "TS-2026-000001-1", estado, url, codigo, descripcion);
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

  private LineaPedido linea() {
    return new LineaPedido(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        2,
        Dinero.deCop(50_000),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.webp",
        UUID.randomUUID());
  }

  @Test
  void devuelveLaUrlDePagoYDejaElPagoPendienteConSuIdDeTransaccion() {
    CrearIntentoDePagoSistecredito caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.SISTECREDITO, 1);

    IntentoDePagoSistecredito intento =
        caso.ejecutar(new CrearIntentoDePagoSistecreditoComando(pedido.id(), DOCUMENTO));

    assertEquals(URL_PAGO, intento.urlRedireccion());
    assertEquals("TS-2026-000001-1", intento.referencia().valor());
    assertEquals(pedido.total(), intento.monto());

    Pago guardado = pagos.buscarPorReferencia(intento.referencia()).orElseThrow();
    assertEquals(EstadoPago.PENDIENTE, guardado.estado());
    assertEquals("649b4c821b581f96e45b5696", guardado.idTransaccionPasarela().orElseThrow());
  }

  /** El monto sale del pedido, nunca del cliente (regla dura #7). */
  @Test
  void elMontoYLaFacturaQueViajanALaPasarelaSalenDelPedido() {
    CrearIntentoDePagoSistecredito caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.SISTECREDITO, 1);

    caso.ejecutar(new CrearIntentoDePagoSistecreditoComando(pedido.id(), DOCUMENTO));

    SolicitudTransaccionSistecredito solicitud = pasarela.ultimaSolicitud();
    assertEquals(pedido.total(), solicitud.monto());
    assertEquals("TS-2026-000001-1", solicitud.referencia().valor());
    assertEquals(DOCUMENTO, solicitud.documento());
    assertFalse(solicitud.sandbox());
  }

  @Test
  void enModoSandboxLaSolicitudDiceQueEstadoSimula() {
    CrearIntentoDePagoSistecredito caso = crear(true, "Approved");
    Pedido pedido = pedidoConMetodo(MetodoPago.SISTECREDITO, 1);

    caso.ejecutar(new CrearIntentoDePagoSistecreditoComando(pedido.id(), DOCUMENTO));

    assertTrue(pasarela.ultimaSolicitud().sandbox());
    assertEquals("Approved", pasarela.ultimaSolicitud().estadoSimulado());
  }

  @Test
  void unSegundoIntentoIncrementaElNumeroDeLaReferencia() {
    CrearIntentoDePagoSistecredito caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.SISTECREDITO, 1);

    caso.ejecutar(new CrearIntentoDePagoSistecreditoComando(pedido.id(), DOCUMENTO));
    pasarela.responder(
        new TransaccionSistecredito(
            "otro-id", "TS-2026-000001-2", "Pending", URL_PAGO, null, null));
    IntentoDePagoSistecredito segundo =
        caso.ejecutar(new CrearIntentoDePagoSistecreditoComando(pedido.id(), DOCUMENTO));

    assertEquals("TS-2026-000001-2", segundo.referencia().valor());
  }

  @Test
  void unPedidoDeOtroMetodoDePagoNoSeCobraPorSistecredito() {
    CrearIntentoDePagoSistecredito caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI, 1);

    assertThrows(
        MetodoDePagoNoEsDeSistecreditoException.class,
        () -> caso.ejecutar(new CrearIntentoDePagoSistecreditoComando(pedido.id(), DOCUMENTO)));
  }

  /**
   * Lo importante de esta prueba no es la excepción: es que el pago quedó guardado con su id. Una
   * transacción rechazada existe del lado de Sistecrédito, y perder su id aquí la dejaría sin nadie
   * que pueda consultarla después.
   */
  @Test
  void unRechazoDelMedioDePagoGuardaIgualElPagoConSuIdYExplicaElCodigo() {
    CrearIntentoDePagoSistecredito caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.SISTECREDITO, 1);
    pasarela.responder(
        transaccion("Rejected", null, "802", "El valor del crédito solicitado es menor al mínimo"));

    SistecreditoNoEntregoLaUrlDePagoException error =
        assertThrows(
            SistecreditoNoEntregoLaUrlDePagoException.class,
            () -> caso.ejecutar(new CrearIntentoDePagoSistecreditoComando(pedido.id(), DOCUMENTO)));

    assertEquals("802", error.codigo());
    assertEquals("Rejected", error.estado());
    Pago guardado = pagos.buscarPorReferencia(new ReferenciaPago("TS-2026-000001-1")).orElseThrow();
    assertEquals("649b4c821b581f96e45b5696", guardado.idTransaccionPasarela().orElseThrow());
  }

  @Test
  void sinDocumentoNoSeLlamaALaPasarela() {
    CrearIntentoDePagoSistecredito caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.SISTECREDITO, 1);

    assertThrows(
        NullPointerException.class,
        () -> caso.ejecutar(new CrearIntentoDePagoSistecreditoComando(pedido.id(), null)));
    assertTrue(pasarela.solicitudes().isEmpty());
  }
}
