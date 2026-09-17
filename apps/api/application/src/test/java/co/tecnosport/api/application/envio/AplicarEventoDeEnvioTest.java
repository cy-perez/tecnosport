package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.pedido.MarcarEntregado;
import co.tecnosport.api.application.pedido.RechazarEnEntrega;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.inventario.Inventario;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El componente por el que entran los dos caminos —webhook y conciliación— y que decide qué mueve
 * el pedido y qué solo se anota (adr/0022).
 *
 * <p>Lo que se prueba no es el camino feliz: es que ninguno de los cuatro desenlaces lance, que un
 * reintento no aplique nada dos veces, y que un estado que llega tarde no reviente contra la
 * máquina de estados del pedido.
 */
class AplicarEventoDeEnvioTest {

  private static final Instant AHORA = Instant.parse("2026-09-12T15:00:00Z");
  private static final Instant DESPACHO = AHORA.minusSeconds(86_400);

  private static final Direccion MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Circular 4 # 70-20", null);

  private RepositorioEnviosFalso envios;
  private RepositorioPedidosFalso pedidos;
  private RepositorioInventarioFalso inventarios;
  private AplicarEventoDeEnvio caso;
  private UUID varianteId;

  @BeforeEach
  void preparar() {
    envios = new RepositorioEnviosFalso();
    pedidos = new RepositorioPedidosFalso();
    inventarios = new RepositorioInventarioFalso();
    caso =
        new AplicarEventoDeEnvio(
            envios,
            pedidos,
            new MarcarEntregado(pedidos, inventarios, () -> AHORA),
            new RechazarEnEntrega(pedidos, inventarios, () -> AHORA),
            () -> AHORA);
  }

  private Pedido sembrarPedidoDespachado(MetodoPago metodoPago) {
    varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(5, "compra inicial", DESPACHO.minusSeconds(3600));
    // Treinta días y no veinticuatro horas: con una reserva ya vencida, liberarla no devuelve
    // nada al saldo disponible y la prueba de la devolución no probaría nada.
    var reserva = inventario.reservar(1, Duration.ofDays(30), DESPACHO.minusSeconds(60));
    inventarios.conInventario(inventario);

    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(89_900),
                    new BigDecimal("0.19"),
                    null,
                    reserva.id())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            MEDELLIN,
            metodoPago,
            "cliente@tecnosport.co",
            DESPACHO.minusSeconds(60));
    if (metodoPago != MetodoPago.CONTRAENTREGA) {
      pedido.transicionar(EstadoPedido.PAGADO, "webhook", "pago aprobado", DESPACHO);
    }
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin", "alistado", DESPACHO);
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin", "despachado", DESPACHO);
    pedidos.guardar(pedido);
    envios.guardar(
        Envio.crear(
            pedido.id(),
            List.of(GuiaEnvio.crear("99 minutes", "NN-1", Dinero.deCop(10_540))),
            DESPACHO));
    return pedido;
  }

  /** Los eventos de una guía concreta: con varias por envío, preguntar por el envío no basta. */
  private List<EventoSeguimiento> eventosDe(String numeroGuia) {
    return envios
        .buscarPorGuia(numeroGuia)
        .orElseThrow()
        .guiaDe(numeroGuia)
        .orElseThrow()
        .eventos();
  }

  private AplicarEventoDeEnvioComando evento(EstadoEnvio estado, String idExterno) {
    return new AplicarEventoDeEnvioComando(
        "NN-1", estado, "novedad de la transportadora", AHORA, idExterno, "skydropx");
  }

  /** Un evento de un despacho que no es nuestro no puede tumbar el webhook. */
  @Test
  void unaGuiaDesconocidaSeDescartaSinLanzar() {
    ResultadoEventoDeEnvio resultado =
        caso.ejecutar(
            new AplicarEventoDeEnvioComando(
                "NO-EXISTE", EstadoEnvio.ENTREGADO, null, AHORA, "ev-1", "skydropx"));

    assertEquals(ResultadoEventoDeEnvio.GUIA_DESCONOCIDA, resultado);
  }

  @Test
  void unEstadoQueNoMueveElPedidoSoloSeAnota() {
    Pedido pedido = sembrarPedidoDespachado(MetodoPago.NEQUI);

    ResultadoEventoDeEnvio resultado = caso.ejecutar(evento(EstadoEnvio.EN_TRANSITO, "ev-1"));

    assertEquals(ResultadoEventoDeEnvio.REGISTRADO, resultado);
    assertEquals(EstadoPedido.DESPACHADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
    assertEquals(1, eventosDe("NN-1").size());
  }

  @Test
  void entregadoMueveElPedidoAEntregado() {
    Pedido pedido = sembrarPedidoDespachado(MetodoPago.NEQUI);

    ResultadoEventoDeEnvio resultado = caso.ejecutar(evento(EstadoEnvio.ENTREGADO, "ev-1"));

    assertEquals(ResultadoEventoDeEnvio.REGISTRADO_Y_APLICADO, resultado);
    assertEquals(EstadoPedido.ENTREGADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  /** En contraentrega la entrega encadena el recaudo pendiente: el dinero todavía no ha entrado. */
  @Test
  void entregarUnContraentregaDejaElRecaudoPendiente() {
    Pedido pedido = sembrarPedidoDespachado(MetodoPago.CONTRAENTREGA);

    caso.ejecutar(evento(EstadoEnvio.ENTREGADO, "ev-1"));

    assertEquals(
        EstadoPedido.RECAUDO_PENDIENTE, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void enDevolucionRechazaElPedidoYLiberaElInventario() {
    Pedido pedido = sembrarPedidoDespachado(MetodoPago.NEQUI);
    int disponibleAntes =
        inventarios.buscarPorVarianteId(varianteId).orElseThrow().saldoDisponible(AHORA);

    ResultadoEventoDeEnvio resultado = caso.ejecutar(evento(EstadoEnvio.EN_DEVOLUCION, "ev-1"));

    assertEquals(ResultadoEventoDeEnvio.REGISTRADO_Y_APLICADO, resultado);
    assertEquals(
        EstadoPedido.RECHAZADO_EN_ENTREGA, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
    assertEquals(
        disponibleAntes + 1,
        inventarios.buscarPorVarianteId(varianteId).orElseThrow().saldoDisponible(AHORA));
  }

  /**
   * La razón de ser de la idempotencia. El webhook reintenta, y aplicar dos veces un entregado
   * reabriría los plazos del retracto y de la garantía, que cuelgan de esa fecha.
   */
  @Test
  void elMismoEventoDosVecesNoAplicaDosVeces() {
    Pedido pedido = sembrarPedidoDespachado(MetodoPago.NEQUI);
    caso.ejecutar(evento(EstadoEnvio.ENTREGADO, "ev-1"));

    ResultadoEventoDeEnvio segundo = caso.ejecutar(evento(EstadoEnvio.ENTREGADO, "ev-1"));

    assertEquals(ResultadoEventoDeEnvio.REPETIDO, segundo);
    assertEquals(1, eventosDe("NN-1").size());
    assertEquals(EstadoPedido.ENTREGADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  /**
   * El caso que revienta si nadie lo guarda: un administrador marcó la entrega a mano y después
   * llega el evento de la transportadora. El pedido ya no está en {@code DESPACHADO}, así que la
   * transición no se intenta — se anota el evento y se sigue.
   */
  @Test
  void unEntregadoQueLlegaTardeSeAnotaSinReventar() {
    Pedido pedido = sembrarPedidoDespachado(MetodoPago.NEQUI);
    pedido.transicionar(EstadoPedido.ENTREGADO, "admin", "entregado a mano", AHORA);
    pedidos.guardar(pedido);

    ResultadoEventoDeEnvio resultado = caso.ejecutar(evento(EstadoEnvio.ENTREGADO, "ev-tarde"));

    assertEquals(ResultadoEventoDeEnvio.REGISTRADO, resultado);
    assertEquals(EstadoPedido.ENTREGADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
    assertEquals(1, eventosDe("NN-1").size());
  }

  /** Los cuatro que piden ojo humano se registran igual y no mueven nada. */
  @Test
  void losEstadosDeAlertaSeRegistranSinMoverElPedido() {
    Pedido pedido = sembrarPedidoDespachado(MetodoPago.NEQUI);

    for (EstadoEnvio estado :
        List.of(
            EstadoEnvio.EXCEPCION,
            EstadoEnvio.RETENIDO,
            EstadoEnvio.CANCELADO,
            EstadoEnvio.DESTRUIDO)) {
      assertEquals(
          ResultadoEventoDeEnvio.REGISTRADO, caso.ejecutar(evento(estado, "ev-" + estado)));
      assertTrue(estado.exigeRevisionManual());
    }

    assertEquals(EstadoPedido.DESPACHADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
    assertEquals(4, eventosDe("NN-1").size());
  }

  @Test
  void elRastroConservaCuandoOcurrioYCuandoLlego() {
    sembrarPedidoDespachado(MetodoPago.NEQUI);
    Instant ocurrio = AHORA.minusSeconds(7200);

    caso.ejecutar(
        new AplicarEventoDeEnvioComando(
            "NN-1", EstadoEnvio.EN_TRANSITO, "en ruta", ocurrio, "ev-1", "skydropx"));

    EventoSeguimiento guardado = eventosDe("NN-1").get(0);
    assertEquals(ocurrio, guardado.ocurrioEn());
    assertEquals(AHORA, guardado.recibidoEn());
  }
}
