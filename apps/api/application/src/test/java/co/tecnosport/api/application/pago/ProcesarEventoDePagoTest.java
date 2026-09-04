package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
  private RepositorioInventarioFalso inventarios;
  private PasarelaDePagosFalsa pasarela;
  private UUID varianteId;

  private ProcesarEventoDePago crear() {
    pedidos = new RepositorioPedidosFalso();
    pagos = new RepositorioPagosFalso();
    inventarios = new RepositorioInventarioFalso();
    pasarela = new PasarelaDePagosFalsa();
    return new ProcesarEventoDePago(pagos, pedidos, inventarios, pasarela, new RelojFalso(AHORA));
  }

  /** Reserva vigente en {@code AHORA}: confirmar/liberar la encuentran válida. */
  private LineaPedido lineaConReservaVigente(int cantidad) {
    varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(10, "siembra de prueba", AHORA);
    MovimientoInventario reserva = inventario.reservar(cantidad, Duration.ofMinutes(30), AHORA);
    inventarios.conInventario(inventario);
    return new LineaPedido(
        UUID.randomUUID(),
        varianteId,
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        cantidad,
        Dinero.deCop(50_000),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.webp",
        reserva.id());
  }

  /** Reserva que ya venció para cuando llega el evento en {@code AHORA}. */
  private LineaPedido lineaConReservaVencida(int cantidad) {
    varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(10, "siembra de prueba", AHORA);
    Instant haceUnaHora = AHORA.minus(Duration.ofHours(1));
    MovimientoInventario reserva =
        inventario.reservar(cantidad, Duration.ofMinutes(30), haceUnaHora);
    inventarios.conInventario(inventario);
    return new LineaPedido(
        UUID.randomUUID(),
        varianteId,
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        cantidad,
        Dinero.deCop(50_000),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.webp",
        reserva.id());
  }

  private Pedido pedidoConLinea(LineaPedido linea) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            CORREO,
            List.of(linea),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.NEQUI,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.conPedido(pedido);
    return pedido;
  }

  private Pedido pedidoConMetodo(MetodoPago metodoPago) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            CORREO,
            List.of(lineaConReservaVigente(2)),
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
  void eventoAprobadoConfirmaLaReservaConvirtiendolaEnSalida() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    pagoPendienteParaElPedido(pedido);

    caso.ejecutar(comando("APPROVED"));

    Inventario inventario = inventarios.buscarPorVarianteId(varianteId).orElseThrow();
    assertEquals(8, inventario.saldoTotal());
    assertEquals(8, inventario.saldoDisponible(AHORA));
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
  void eventoRechazadoLiberaLaReserva() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    pagoPendienteParaElPedido(pedido);

    caso.ejecutar(comando("DECLINED"));

    Inventario inventario = inventarios.buscarPorVarianteId(varianteId).orElseThrow();
    assertEquals(10, inventario.saldoTotal());
    assertEquals(10, inventario.saldoDisponible(AHORA));
  }

  @Test
  void eventoAprobadoConReservaYaVencidaQuedaSinConfirmarInventarioPeroElPedidoQuedaPagado() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConLinea(lineaConReservaVencida(2));
    pagoPendienteParaElPedido(pedido);

    ResultadoEventoDePago resultado = caso.ejecutar(comando("APPROVED"));

    assertEquals(ResultadoEventoDePago.APLICADO_SIN_CONFIRMAR_INVENTARIO, resultado);
    assertEquals(EstadoPedido.PAGADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
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
