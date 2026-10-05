package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcesarEventoDePagoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final CorreoElectronico CORREO = new CorreoElectronico("cliente@tecnosport.co");
  private static final Direccion DIRECCION_MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");
  private static final ReferenciaPago REFERENCIA = new ReferenciaPago("TS-2026-000001-1");
  private static final List<String> VALORES_FIRMA = List.of("wompi-tx-1", "APPROVED", "100000");
  private static final long TIMESTAMP_FIRMA = 1_700_000_000L;
  private static final String CHECKSUM = "checksum-del-evento-1";
  private static final String ID_TRANSACCION = "wompi-tx-1";
  private static final List<String> PROPIEDADES_FIRMADAS =
      List.of("transaction.id", "transaction.status", "transaction.amount_in_cents");

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
            MetodoPago.WOMPI,
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
    return comando(estadoWompi, null);
  }

  private ProcesarEventoDePagoComando comando(String estadoWompi, String medioWompi) {
    return comandoConChecksum(estadoWompi, medioWompi, CHECKSUM);
  }

  /**
   * El checksum es el id de evento, así que cambiarlo es lo que distingue "el mismo webhook
   * repetido" de "otro webhook sobre el mismo pago".
   */
  private ProcesarEventoDePagoComando comandoConChecksum(
      String estadoWompi, String medioWompi, String checksum) {
    return comandoCompleto(
        estadoWompi, medioWompi, checksum, PROPIEDADES_FIRMADAS, centavosDelPago(), "COP");
  }

  /** Lo que de verdad cobra el pago sembrado: el evento normal trae ese monto. */
  private Long centavosDelPago() {
    return pagos
        .buscarPorReferencia(REFERENCIA)
        .map(p -> p.monto().valor().longValueExact() * 100)
        .orElse(0L);
  }

  private ProcesarEventoDePagoComando comandoCompleto(
      String estadoWompi,
      String medioWompi,
      String checksum,
      List<String> propiedadesFirmadas,
      Long centavos,
      String moneda) {
    return new ProcesarEventoDePagoComando(
        REFERENCIA.valor(),
        estadoWompi,
        medioWompi,
        VALORES_FIRMA,
        TIMESTAMP_FIRMA,
        checksum,
        propiedadesFirmadas,
        ID_TRANSACCION,
        centavos,
        moneda);
  }

  /**
   * El hueco que esto cierra: la URL del Web Checkout hospedado no le manda a Wompi el método que
   * el comprador eligió aquí, así que Wompi pinta su propia lista y el comprador vuelve a elegir.
   * El pedido decía NEQUI y nada contrastaba eso contra lo que se cobró de verdad.
   */
  @Test
  void elEventoGuardaConQueSeCobroDeVerdadSinTocarElMetodoElegido() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);

    caso.ejecutar(comando("APPROVED", "CARD"));

    Pago pago = pagos.buscarPorReferencia(REFERENCIA).orElseThrow();
    assertEquals("CARD", pago.medioReportadoPorLaPasarela().orElseThrow());
    assertEquals(MetodoPago.WOMPI, pago.metodoPago());
  }

  /**
   * Un evento sin ese campo sigue cerrando el pago: el estado es lo que decide, el medio informa.
   */
  @Test
  void unEventoSinElMedioNoImpideAplicarlo() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);

    ResultadoEventoDePago resultado = caso.ejecutar(comando("APPROVED", null));

    assertEquals(ResultadoEventoDePago.APLICADO, resultado);
    assertTrue(
        pagos
            .buscarPorReferencia(REFERENCIA)
            .orElseThrow()
            .medioReportadoPorLaPasarela()
            .isEmpty());
  }

  @Test
  void eventoAprobadoTransicionaElPagoYElPedido() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);

    ResultadoEventoDePago resultado = caso.ejecutar(comando("APPROVED"));

    assertEquals(ResultadoEventoDePago.APLICADO, resultado);
    assertEquals(EstadoPago.APROBADO, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
    // Encadena PAGADO -> EN_PREPARACION de una vez: con el inventario confirmado no hay nada que
    // esperar para que el pedido quede listo para preparar.
    assertEquals(
        EstadoPedido.EN_PREPARACION, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void eventoAprobadoConfirmaLaReservaConvirtiendolaEnSalida() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);

    caso.ejecutar(comando("APPROVED"));

    Inventario inventario = inventarios.buscarPorVarianteId(varianteId).orElseThrow();
    assertEquals(8, inventario.saldoTotal());
    assertEquals(8, inventario.saldoDisponible(AHORA));
  }

  @Test
  void eventoRechazadoDejaElPedidoEnPagoFallido() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);

    caso.ejecutar(comando("DECLINED"));

    assertEquals(
        EstadoPedido.PAGO_FALLIDO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void eventoRechazadoLiberaLaReserva() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
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
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);
    caso.ejecutar(comando("APPROVED"));

    ResultadoEventoDePago segundaVez = caso.ejecutar(comando("APPROVED"));

    assertEquals(ResultadoEventoDePago.YA_PROCESADO, segundaVez);
    assertEquals(1, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().eventos().size());
  }

  /**
   * El webhook que no estaba perdido sino tarde. La conciliación ya resolvió el pago con su propio
   * id de evento —{@code conciliacion:<id>:APPROVED}— y ahora llega el de Wompi, cuyo id es el
   * checksum: otro distinto. La desduplicación por id no lo reconoce, y sin la guarda de estado la
   * máquina de estados salta con una excepción de dominio que sale como 422 al endpoint que promete
   * no devolver nunca otra cosa que 200. Ante un no-2xx la pasarela reintenta, así que el mismo
   * evento volvía indefinidamente.
   */
  @Test
  void unWebhookTardioSobreUnPagoYaResueltoSeCuentaComoProcesadoYNoRevienta() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);
    caso.ejecutar(comando("APPROVED"));

    ResultadoEventoDePago tardio =
        caso.ejecutar(comandoConChecksum("APPROVED", null, "otro-checksum-de-wompi"));

    assertEquals(ResultadoEventoDePago.YA_PROCESADO, tardio);
    // Y no se le añadió un segundo evento al pago.
    assertEquals(1, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().eventos().size());
  }

  @Test
  void firmaInvalidaNoAplicaNada() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
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
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);

    ResultadoEventoDePago resultado = caso.ejecutar(comando("VOIDED"));

    assertEquals(ResultadoEventoDePago.ESTADO_NO_SOPORTADO, resultado);
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
  }

  @Test
  void unPedidoYaResueltoPorOtroIntentoNoSeToca() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);
    pedido.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "otro intento aprobado", AHORA);
    pedidos.guardar(pedido);

    ResultadoEventoDePago resultado = caso.ejecutar(comando("DECLINED"));

    assertEquals(ResultadoEventoDePago.APLICADO, resultado);
    assertEquals(
        EstadoPago.RECHAZADO, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
    assertEquals(EstadoPedido.PAGADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  /**
   * El comprador pagó dos intentos: el pedido ya está pagado por el primero cuando llega la
   * aprobación del segundo. Antes esto devolvía APLICADO y el dinero quedaba cobrado sin venta.
   */
  @Test
  void unaAprobacionSobreUnPedidoYaPagadoQuedaMarcadaParaDevolver() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);
    pedido.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "otro intento aprobado", AHORA);
    pedidos.guardar(pedido);
    int movimientosAntes =
        inventarios.buscarPorVarianteId(varianteId).orElseThrow().movimientos().size();

    ResultadoEventoDePago resultado = caso.ejecutar(comando("APPROVED"));

    assertEquals(ResultadoEventoDePago.APROBADO_SIN_PEDIDO_QUE_LO_ESPERE, resultado);
    Pago guardado = pagos.buscarPorReferencia(REFERENCIA).orElseThrow();
    assertEquals(EstadoPago.APROBADO, guardado.estado());
    assertEquals(Optional.of(AHORA), guardado.sinPedidoQueLoEspereDesde());
    assertEquals(EstadoPedido.PAGADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
    assertEquals(
        movimientosAntes,
        inventarios.buscarPorVarianteId(varianteId).orElseThrow().movimientos().size());
  }

  /** Cancelado antes de que entrara el dinero: el pago aprobado tarde se devuelve, no se pierde. */
  @Test
  void unaAprobacionSobreUnPedidoCanceladoQuedaMarcadaParaDevolver() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);
    pedido.transicionar(EstadoPedido.CANCELADO, "admin:1", "sin existencia", AHORA);
    pedidos.guardar(pedido);

    assertEquals(
        ResultadoEventoDePago.APROBADO_SIN_PEDIDO_QUE_LO_ESPERE,
        caso.ejecutar(comando("APPROVED")));
    assertEquals(EstadoPedido.CANCELADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
    assertTrue(
        pagos
            .buscarPorReferencia(REFERENCIA)
            .orElseThrow()
            .sinPedidoQueLoEspereDesde()
            .isPresent());
  }

  /** Un intento falló, el pedido quedó en PAGO_FALLIDO, y después otro intento vivo se aprobó. */
  @Test
  void unaAprobacionSobreUnPedidoConPagoFallidoQuedaMarcadaParaDevolver() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);
    pedido.transicionar(EstadoPedido.PAGO_FALLIDO, "webhook-wompi", "otro intento falló", AHORA);
    pedidos.guardar(pedido);

    assertEquals(
        ResultadoEventoDePago.APROBADO_SIN_PEDIDO_QUE_LO_ESPERE,
        caso.ejecutar(comando("APPROVED")));
    assertEquals(
        EstadoPedido.PAGO_FALLIDO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  /** Lo normal no cambia: el pedido que sí esperaba el pago no queda marcado. */
  @Test
  void unaAprobacionQueSiEsperabaSuPedidoNoSeMarca() {
    ProcesarEventoDePago caso = crear();
    pagoPendienteParaElPedido(pedidoConMetodo(MetodoPago.WOMPI));

    caso.ejecutar(comando("APPROVED"));

    assertTrue(
        pagos.buscarPorReferencia(REFERENCIA).orElseThrow().sinPedidoQueLoEspereDesde().isEmpty());
  }

  /**
   * La contraprueba que el webhook no hacía: un evento firmado con la referencia del pago pero otro
   * importe no se aplica. La conciliación ya comparaba; el camino principal se fiaba.
   */
  @Test
  void unEventoConOtroMontoNoAplicaNada() {
    ProcesarEventoDePago caso = crear();
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    pagoPendienteParaElPedido(pedido);

    ResultadoEventoDePago resultado =
        caso.ejecutar(
            comandoCompleto("APPROVED", null, CHECKSUM, PROPIEDADES_FIRMADAS, 2_000_000L, "COP"));

    assertEquals(ResultadoEventoDePago.MONTO_NO_COINCIDE, resultado);
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
    assertEquals(
        EstadoPedido.PAGO_PENDIENTE, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void unEventoEnOtraMonedaNoAplicaNada() {
    ProcesarEventoDePago caso = crear();
    pagoPendienteParaElPedido(pedidoConMetodo(MetodoPago.WOMPI));

    assertEquals(
        ResultadoEventoDePago.MONTO_NO_COINCIDE,
        caso.ejecutar(
            comandoCompleto(
                "APPROVED", null, CHECKSUM, PROPIEDADES_FIRMADAS, centavosDelPago(), "USD")));
  }

  @Test
  void unEventoSinMontoNoSeAplicaYQuedaParaLaConciliacion() {
    ProcesarEventoDePago caso = crear();
    pagoPendienteParaElPedido(pedidoConMetodo(MetodoPago.WOMPI));

    assertEquals(
        ResultadoEventoDePago.MONTO_NO_COINCIDE,
        caso.ejecutar(
            comandoCompleto("APPROVED", null, CHECKSUM, PROPIEDADES_FIRMADAS, null, "COP")));
  }

  /** Una firma válida que no cubre el estado no autentica el APPROVED que se leería. */
  @Test
  void unaFirmaQueNoCubreElEstadoNoAplicaNada() {
    ProcesarEventoDePago caso = crear();
    pagoPendienteParaElPedido(pedidoConMetodo(MetodoPago.WOMPI));

    ResultadoEventoDePago resultado =
        caso.ejecutar(
            comandoCompleto(
                "APPROVED",
                null,
                CHECKSUM,
                List.of("transaction.id", "transaction.amount_in_cents"),
                centavosDelPago(),
                "COP"));

    assertEquals(ResultadoEventoDePago.ESTADO_SIN_FIRMAR, resultado);
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
  }

  /**
   * El webhook deja el id de la transacción: un comprador que cerró la pestaña sin volver del
   * checkout ya no deja el pago sin id para la conciliación.
   */
  @Test
  void elWebhookRegistraElIdDeLaTransaccion() {
    ProcesarEventoDePago caso = crear();
    pagoPendienteParaElPedido(pedidoConMetodo(MetodoPago.WOMPI));

    caso.ejecutar(comando("APPROVED"));

    assertEquals(
        Optional.of(ID_TRANSACCION),
        pagos.buscarPorReferencia(REFERENCIA).orElseThrow().idTransaccionPasarela());
  }
}
