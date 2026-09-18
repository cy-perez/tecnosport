package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.RepositorioReintegrosFalso;
import co.tecnosport.api.application.compartido.RepositorioSolicitudesReversionFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.application.envio.ResultadoCancelacion;
import co.tecnosport.api.application.reintegro.ReintegroRequeridoException;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.inventario.TipoMovimientoInventario;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.MotivoCancelacion;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CancelarPedidoTest {

  private static final Instant AHORA = Instant.parse("2026-09-10T12:00:00Z");

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final RepositorioReintegrosFalso reintegros = new RepositorioReintegrosFalso();

  // El tope cuenta tambien lo que revirtio el emisor, que vive en las reversiones del pedido.
  private final RepositorioSolicitudesReversionFalso reversionesParaElTope =
      new RepositorioSolicitudesReversionFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();
  private final RepositorioEmisionesFalso emisiones = new RepositorioEmisionesFalso();
  private final EmisorDeGuiasFalso emisor = new EmisorDeGuiasFalso();

  private UUID varianteId;
  private UUID idReserva;

  private CancelarPedido casoDeUso() {
    return new CancelarPedido(
        pedidos,
        inventarios,
        reintegros,
        emisiones,
        emisor,
        new TopeDeReintegro(reintegros, reversionesParaElTope),
        correos,
        new TextosDeCorreoFalso(),
        new RelojFalso(AHORA));
  }

  /** Cinco unidades, una reservada, confirmada o no segun si el pago ya entro. */
  private void inventarioConLaReserva(boolean reservaConfirmada) {
    varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(5, "siembra", AHORA.minusSeconds(1000));
    MovimientoInventario reserva =
        inventario.reservar(1, Duration.ofMinutes(30), AHORA.minusSeconds(900));
    idReserva = reserva.id();
    if (reservaConfirmada) {
      inventario.confirmar(idReserva, AHORA.minusSeconds(800));
    }
    inventarios.conInventario(inventario);
  }

  private Pedido pedidoEn(EstadoPedido estado, MetodoPago metodoPago) {
    inventarioConLaReserva(metodoPago != MetodoPago.CONTRAENTREGA);
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
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    idReserva)),
            TipoEntrega.ENVIO_A_DOMICILIO,
            Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            metodoPago,
            "cliente@tecnosport.co",
            AHORA.minusSeconds(500));
    // Pedido.crear ya deja el pedido en PAGO_PENDIENTE o en CONFIRMADO_CONTRAENTREGA segun el
    // metodo de pago: desde ahi solo hay que avanzar lo que pida el escenario.
    if (metodoPago != MetodoPago.CONTRAENTREGA && estado != EstadoPedido.PAGO_PENDIENTE) {
      pedido.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "pago aprobado", AHORA);
    }
    if (estado == EstadoPedido.EN_PREPARACION) {
      pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:1", "listo", AHORA);
    }
    pedidos.guardar(pedido);
    return pedido;
  }

  private Inventario inventarioDeLaLinea() {
    return inventarios.buscarPorVarianteId(varianteId).orElseThrow();
  }

  private long movimientosDeTipo(TipoMovimientoInventario tipo) {
    return inventarioDeLaLinea().movimientos().stream().filter(m -> m.tipo() == tipo).count();
  }

  /**
   * El camino de "Disponibilidad": el stock desaparecio despues de la compra. La unidad vuelve a
   * estar vendible y el dinero se devuelve, porque en un pago en linea ya habia entrado.
   */
  @Test
  void porNoDisponibilidadDevuelveLaUnidadYElDinero() {
    Pedido pedido = pedidoEn(EstadoPedido.PAGADO, MetodoPago.NEQUI);

    casoDeUso()
        .ejecutar(
            new CancelarPedidoComando(
                pedido.id(),
                MotivoCancelacion.NO_DISPONIBILIDAD,
                BigDecimal.valueOf(50_000),
                MedioReintegro.WOMPI,
                "DEV-1",
                "admin:1"));

    assertEquals(EstadoPedido.CANCELADO, pedido.estado());
    assertEquals(5, inventarioDeLaLinea().saldoTotal(), "saldo total");
    assertEquals(5, inventarioDeLaLinea().saldoDisponible(AHORA), "saldo disponible");
    assertEquals(1, reintegros.guardados().size());
    assertEquals(MotivoReintegro.NO_DISPONIBILIDAD, reintegros.guardados().get(0).motivo());
  }

  /** El camino de "Envio y entrega": no se entrego a tiempo y el comprador termina el contrato. */
  @Test
  void porPlazoIncumplidoElReintegroLlevaSuPropioMotivo() {
    Pedido pedido = pedidoEn(EstadoPedido.EN_PREPARACION, MetodoPago.NEQUI);

    casoDeUso()
        .ejecutar(
            new CancelarPedidoComando(
                pedido.id(),
                MotivoCancelacion.PLAZO_INCUMPLIDO,
                BigDecimal.valueOf(50_000),
                MedioReintegro.WOMPI,
                null,
                "admin:1"));

    assertEquals(MotivoReintegro.PLAZO_INCUMPLIDO, reintegros.guardados().get(0).motivo());
    assertEquals(pedido.id(), reintegros.guardados().get(0).origenId());
  }

  /**
   * Un contraentrega sin despachar no cobro nada: exigir una constancia obligaria a inventar un
   * reintegro que nunca ocurrio. Y su reserva estaba abierta, asi que vuelve como liberacion.
   */
  @Test
  void unContraentregaSinDespacharNoDejaConstanciaDeDinero() {
    Pedido pedido = pedidoEn(EstadoPedido.CONFIRMADO_CONTRAENTREGA, MetodoPago.CONTRAENTREGA);

    casoDeUso()
        .ejecutar(
            new CancelarPedidoComando(
                pedido.id(), MotivoCancelacion.NO_DISPONIBILIDAD, null, null, null, "admin:1"));

    assertEquals(EstadoPedido.CANCELADO, pedido.estado());
    assertTrue(reintegros.guardados().isEmpty(), "no salio dinero porque nunca entro");
    assertEquals(1, movimientosDeTipo(TipoMovimientoInventario.LIBERACION), "liberaciones");
    assertEquals(5, inventarioDeLaLinea().saldoDisponible(AHORA));
  }

  @Test
  void unPagoPendienteTampocoDejaConstanciaDeDinero() {
    Pedido pedido = pedidoEn(EstadoPedido.PAGO_PENDIENTE, MetodoPago.NEQUI);

    casoDeUso()
        .ejecutar(
            new CancelarPedidoComando(
                pedido.id(), MotivoCancelacion.NO_DISPONIBILIDAD, null, null, null, "admin:1"));

    assertEquals(EstadoPedido.CANCELADO, pedido.estado());
    assertTrue(reintegros.guardados().isEmpty());
  }

  /**
   * Y al reves: si el dinero ya entro, cancelar sin devolverlo es plata retenida sin explicacion.
   * El caso de uso lo bloquea en vez de dejar que el panel lo deje pasar en silencio.
   */
  @Test
  void siElDineroYaEntroNoSePuedeCancelarSinReintegro() {
    Pedido pedido = pedidoEn(EstadoPedido.PAGADO, MetodoPago.NEQUI);

    assertThrows(
        ReintegroRequeridoException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new CancelarPedidoComando(
                        pedido.id(),
                        MotivoCancelacion.NO_DISPONIBILIDAD,
                        null,
                        null,
                        null,
                        "admin:1")));
    assertEquals(EstadoPedido.PAGADO, pedido.estado(), "el pedido no se movio");
    assertEquals(4, inventarioDeLaLinea().saldoTotal(), "ni el inventario");
  }

  /** Despues de despachar ya existen los caminos que corresponden: entrega, rechazo, devolucion. */
  @Test
  void unPedidoDespachadoNoSeCancela() {
    Pedido pedido = pedidoEn(EstadoPedido.EN_PREPARACION, MetodoPago.NEQUI);
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:1", "guia 123", AHORA);
    pedidos.guardar(pedido);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new CancelarPedidoComando(
                        pedido.id(),
                        MotivoCancelacion.PLAZO_INCUMPLIDO,
                        BigDecimal.valueOf(50_000),
                        MedioReintegro.WOMPI,
                        null,
                        "admin:1")));
  }

  /** "Te lo comunicaremos de inmediato", dice el texto de Disponibilidad. */
  @Test
  void avisaAlCompradorDeQueSuPedidoNoVaALlegar() {
    Pedido pedido = pedidoEn(EstadoPedido.PAGADO, MetodoPago.NEQUI);

    casoDeUso()
        .ejecutar(
            new CancelarPedidoComando(
                pedido.id(),
                MotivoCancelacion.NO_DISPONIBILIDAD,
                BigDecimal.valueOf(50_000),
                MedioReintegro.WOMPI,
                null,
                "admin:1"));

    assertEquals(1, correos.enviados().size());
    assertTrue(
        correos.enviados().get(0).asunto().contains(pedido.numeroPedido().valor()),
        "el asunto cita el pedido");
  }

  @Test
  void noSeDevuelveMasDeLoQueSePago() {
    Pedido pedido = pedidoEn(EstadoPedido.PAGADO, MetodoPago.NEQUI);

    assertThrows(
        co.tecnosport.api.application.reintegro.MontoDeReintegroInvalidoException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new CancelarPedidoComando(
                        pedido.id(),
                        MotivoCancelacion.NO_DISPONIBILIDAD,
                        BigDecimal.valueOf(50_001),
                        MedioReintegro.WOMPI,
                        null,
                        "admin:1")));
  }

  @Test
  void unPedidoInexistenteFalla() {
    assertThrows(
        PedidoNoEncontradoException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    new CancelarPedidoComando(
                        UUID.randomUUID(),
                        MotivoCancelacion.NO_DISPONIBILIDAD,
                        null,
                        null,
                        null,
                        "admin:1")));
  }

  // --- la guía del pedido cancelado -------------------------------------------------------------

  /**
   * Una emisión por pedido y un envío por bulto: dos bultos son dos envíos, y se piden por separado
   * porque la plataforma los cancela por separado.
   */
  private EmisionDeGuia emisionEmitidaDe(Pedido pedido, String... envios) {
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Servientrega", "tarifa-1", "admin:1", AHORA);
    emision.aceptada(List.of(envios), AHORA);
    emision.resolver(EstadoEmision.EMITIDA, null, AHORA);
    emisiones.guardar(emision);
    return emision;
  }

  private void cancelar(Pedido pedido) {
    casoDeUso()
        .ejecutar(
            new CancelarPedidoComando(
                pedido.id(),
                MotivoCancelacion.NO_DISPONIBILIDAD,
                BigDecimal.valueOf(50_000),
                MedioReintegro.WOMPI,
                "DEV-1",
                "admin:1"));
  }

  /**
   * Ningún bloqueo de inventario se sostiene mientras se habla con la plataforma.
   *
   * <p>En producción cada {@code buscarPorVarianteId} toma un bloqueo pesimista sobre la fila de
   * esa variante, y anular guías son N llamadas HTTP a Skydropx dentro de la misma transacción. Con
   * el orden anterior, cancelar desde el panel un pedido de la variante más vendida dejaba el
   * checkout de cualquier otro comprador esperando a que el proveedor contestara. Lo levantó una
   * revisión adversarial.
   */
  @Test
  void anularLasGuiasNoSostieneNingunBloqueoDeInventario() {
    Pedido pedido = pedidoEn(EstadoPedido.EN_PREPARACION, MetodoPago.NEQUI);
    emisionEmitidaDe(pedido, "envio-1", "envio-2");
    int[] consultasAlHablarConElProveedor = {-1};
    emisor.mientrasCancelaHaz(
        () -> consultasAlHablarConElProveedor[0] = inventarios.consultasConBloqueo());

    cancelar(pedido);

    assertEquals(
        0,
        consultasAlHablarConElProveedor[0],
        "se habló con la plataforma con bloqueos de inventario ya tomados");
    // Y el inventario sí se devolvió: el orden cambió, no lo que hace.
    assertTrue(inventarios.consultasConBloqueo() > 0);
  }

  @Test
  void cancelarUnPedidoConGuiaEmitidaLaAnulaEnLaPlataforma() {
    Pedido pedido = pedidoEn(EstadoPedido.EN_PREPARACION, MetodoPago.NEQUI);
    EmisionDeGuia emision = emisionEmitidaDe(pedido, "envio-1", "envio-2");

    cancelar(pedido);

    assertEquals(List.of("envio-1", "envio-2"), emisor.cancelados(), "un envío, una llamada");
    assertEquals(EstadoEmision.ANULADA, emision.estado());
  }

  /**
   * La regla que ordena todo lo demás. Si la plataforma no anula, el comprador igual recupera su
   * plata: dejar el pedido sin cancelar por un proveedor caído convertiría un problema nuestro en
   * uno suyo.
   */
  @Test
  void siLaPlataformaNoAnulaElPedidoSeCancelaIgualYLaEmisionPideOjoHumano() {
    Pedido pedido = pedidoEn(EstadoPedido.EN_PREPARACION, MetodoPago.NEQUI);
    EmisionDeGuia emision = emisionEmitidaDe(pedido, "envio-1");
    emisor.alCancelarResponde(new ResultadoCancelacion.NoSePudo("la plataforma respondio 500"));

    cancelar(pedido);

    assertEquals(EstadoPedido.CANCELADO, pedido.estado(), "el pedido se cancela igual");
    assertEquals(1, reintegros.guardados().size(), "y el dinero vuelve igual");
    assertEquals(5, inventarioDeLaLinea().saldoTotal(), "y el inventario también");
    assertEquals(EstadoEmision.SIN_ANULAR, emision.estado());
    assertTrue(emision.estado().exigeOjoHumano(), "sale en la bandeja de revisión");
    assertTrue(
        emision.detalle().orElseThrow().contains("envio-1"),
        "el detalle nombra el envío que hay que anular a mano");
  }

  /**
   * Con un envío anulado y otro en duda, la emisión entera pide ojo humano. El que quedó vivo es el
   * que cuesta, y contarla como anulada porque la mayoría salió bien esconde justo eso.
   */
  @Test
  void bastaConQueUnEnvioQuedeEnDudaParaQueLaEmisionNoCuenteComoAnulada() {
    Pedido pedido = pedidoEn(EstadoPedido.EN_PREPARACION, MetodoPago.NEQUI);
    EmisionDeGuia emision = emisionEmitidaDe(pedido, "envio-1", "envio-2");
    emisor.alCancelarResponde(new ResultadoCancelacion.NoSePudo("sin respuesta"));

    cancelar(pedido);

    assertEquals(EstadoEmision.SIN_ANULAR, emision.estado());
  }

  /**
   * Una emisión fallida ya fue reembolsada por la plataforma y no tiene nada vivo. Pedir su
   * anulación sería gastar una llamada contra un proveedor limitado a dos peticiones por segundo
   * para que responda que no hay nada.
   */
  @Test
  void unaEmisionFallidaNoSeIntentaAnular() {
    Pedido pedido = pedidoEn(EstadoPedido.EN_PREPARACION, MetodoPago.NEQUI);
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Servientrega", "tarifa-1", "admin:1", AHORA);
    emision.resolver(EstadoEmision.FALLIDA, "la transportadora no la acepto", AHORA);
    emisiones.guardar(emision);

    cancelar(pedido);

    assertTrue(emisor.cancelados().isEmpty());
    assertEquals(EstadoEmision.FALLIDA, emision.estado());
  }

  /**
   * Una emisión sin identificadores no se puede anular por API, y aun así puede tener algo vivo: la
   * petición pudo salir. Darla por limpia es justo el silencio que deja una guía cobrable suelta.
   */
  @Test
  void unaEmisionSinIdentificadoresVaALaBandejaEnVezDeDarsePorLimpia() {
    Pedido pedido = pedidoEn(EstadoPedido.EN_PREPARACION, MetodoPago.NEQUI);
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Servientrega", "tarifa-1", "admin:1", AHORA);
    emisiones.guardar(emision);

    cancelar(pedido);

    assertTrue(emisor.cancelados().isEmpty(), "no hay a quién pedírselo");
    assertEquals(EstadoEmision.SIN_ANULAR, emision.estado());
    assertTrue(
        emision.detalle().orElseThrow().contains("tarifa-1"),
        "el detalle lleva la tarifa, que es con lo que se busca en el panel");
  }

  /** Lo más común: un pedido que nunca llegó a pedir guía no llama a la plataforma. */
  @Test
  void unPedidoSinEmisionNoLlamaALaPlataforma() {
    Pedido pedido = pedidoEn(EstadoPedido.PAGADO, MetodoPago.NEQUI);

    cancelar(pedido);

    assertTrue(emisor.cancelados().isEmpty());
  }
}
