package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pago.EstadoPago;
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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcesarEventoDePagoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final CorreoElectronico CORREO = new CorreoElectronico("cliente@tecnosport.co");
  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");
  private static final ReferenciaPago REFERENCIA = new ReferenciaPago("TS-2026-000001-1");
  private static final List<String> VALORES_FIRMA = List.of("wompi-tx-1", "APPROVED", "100000");
  private static final long TIMESTAMP_FIRMA = 1_700_000_000L;
  private static final String CHECKSUM = "checksum-del-evento-1";

  private RepositorioPedidosFalso pedidos;
  private RepositorioPagosFalso pagos;
  private PasarelaDePagosFalsa pasarela;

  private ProcesarEventoDePago crear() {
    pedidos = new RepositorioPedidosFalso();
    pagos = new RepositorioPagosFalso();
    pasarela = new PasarelaDePagosFalsa();
    return new ProcesarEventoDePago(pagos, pedidos, pasarela, new RelojFalso(AHORA));
  }

  private LineaPedido linea() {
    return new LineaPedido(
        java.util.UUID.randomUUID(),
        java.util.UUID.randomUUID(),
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        2,
        Dinero.deCop(50_000),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.webp",
        java.util.UUID.randomUUID());
  }

  private Pedido pedidoConMetodo(MetodoPago metodoPago) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
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

  private Pago pagoPendienteParaElPedido(Pedido pedido) {
    Pago pago = Pago.crear(pedido.id(), REFERENCIA, pedido.metodoPago(), pedido.total(), AHORA);
    pagos.guardar(pago);
    return pago;
  }

  private ProcesarEventoDePagoComando comando(String estadoWompi) {
    return new ProcesarEventoDePagoComando(
        REFERENCIA.valor(), estadoWompi, VALORES_FIRMA, TIMESTAMP_FIRMA, CHECKSUM);
  }

  @Test
  void eventoAprobadoTransicionaElPagoYElPedido() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    pagoPendienteParaElPedido(pedido);

    ResultadoEventoDePago resultado = caso.ejecutar(comando("APPROVED"));

    assertEquals(ResultadoEventoDePago.APLICADO, resultado);
    assertEquals(EstadoPago.APROBADO, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
    assertEquals(EstadoPedido.PAGADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void eventoRechazadoDejaElPedidoEnPagoFallido() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    pagoPendienteParaElPedido(pedido);

    caso.ejecutar(comando("DECLINED"));

    assertEquals(
        EstadoPedido.PAGO_FALLIDO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void eventoConElMismoChecksumDosVecesNoSeReaplica() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    pagoPendienteParaElPedido(pedido);
    caso.ejecutar(comando("APPROVED"));

    ResultadoEventoDePago segundaVez = caso.ejecutar(comando("APPROVED"));

    assertEquals(ResultadoEventoDePago.YA_PROCESADO, segundaVez);
    assertEquals(1, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().eventos().size());
  }

  @Test
  void firmaInvalidaNoAplicaNada() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    pagoPendienteParaElPedido(pedido);
    pasarela.conFirmaEventoInvalida();

    ResultadoEventoDePago resultado = caso.ejecutar(comando("APPROVED"));

    assertEquals(ResultadoEventoDePago.FIRMA_INVALIDA, resultado);
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
    assertEquals(
        EstadoPedido.PAGO_PENDIENTE, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void referenciaInexistenteDevuelvePagoNoEncontrado() {
    ProcesarEventoDePago caso = crear();

    ResultadoEventoDePago resultado = caso.ejecutar(comando("APPROVED"));

    assertEquals(ResultadoEventoDePago.PAGO_NO_ENCONTRADO, resultado);
  }

  @Test
  void estadoVoidedNoSeAplica() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    pagoPendienteParaElPedido(pedido);

    ResultadoEventoDePago resultado = caso.ejecutar(comando("VOIDED"));

    assertEquals(ResultadoEventoDePago.ESTADO_NO_SOPORTADO, resultado);
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
  }

  @Test
  void unPedidoYaResueltoPorOtroIntentoNoSeToca() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    pagoPendienteParaElPedido(pedido);
    pedido.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "otro intento aprobado", AHORA);
    pedidos.guardar(pedido);

    ResultadoEventoDePago resultado = caso.ejecutar(comando("DECLINED"));

    assertEquals(ResultadoEventoDePago.APLICADO, resultado);
    assertEquals(
        EstadoPago.RECHAZADO, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
    assertEquals(EstadoPedido.PAGADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }
}
