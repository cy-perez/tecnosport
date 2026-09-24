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

/**
 * Lo que estas pruebas protegen es el único punto de esta integración donde un desconocido puede
 * escribirnos: el endpoint de confirmación es público y la notificación no viene firmada ({@code
 * adr/0048}). Si el contraste contra la pasarela se afloja, aprobar un pedido pasa a costar una
 * petición HTTP desde cualquier parte del mundo.
 */
class ProcesarNotificacionSistecreditoTest {

  private static final Instant AHORA = Instant.parse("2026-09-20T12:00:00Z");
  private static final CorreoElectronico CORREO = new CorreoElectronico("cliente@tecnosport.co");
  private static final Direccion DIRECCION =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");
  private static final String ID_TRANSACCION = "649b4c821b581f96e45b5696";
  private static final ReferenciaPago REFERENCIA = new ReferenciaPago("TS-2026-000001-1");

  private RepositorioPagosFalso pagos;
  private RepositorioPedidosFalso pedidos;
  private RepositorioInventarioFalso inventarios;
  private PasarelaSistecreditoFalsa pasarela;
  private Pedido pedido;

  private ProcesarNotificacionSistecredito crear() {
    pagos = new RepositorioPagosFalso();
    pedidos = new RepositorioPedidosFalso();
    inventarios = new RepositorioInventarioFalso();
    pasarela = new PasarelaSistecreditoFalsa();
    pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            CORREO,
            List.of(linea()),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION,
            MetodoPago.SISTECREDITO,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.conPedido(pedido);
    Pago pago = Pago.crear(pedido.id(), REFERENCIA, MetodoPago.SISTECREDITO, pedido.total(), AHORA);
    pago.registrarIdTransaccionPasarela(ID_TRANSACCION);
    pagos.guardar(pago);
    return new ProcesarNotificacionSistecredito(
        pagos, pedidos, inventarios, pasarela, new RelojFalso(AHORA));
  }

  /** Con reserva vigente: confirmar o liberar la encuentran válida, como en un pedido real. */
  private LineaPedido linea() {
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(10, "siembra de prueba", AHORA);
    MovimientoInventario reserva = inventario.reservar(1, Duration.ofMinutes(30), AHORA);
    inventarios.conInventario(inventario);
    return new LineaPedido(
        UUID.randomUUID(),
        varianteId,
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        1,
        Dinero.deCop(80_000),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.webp",
        reserva.id());
  }

  private static TransaccionSistecredito laPasarelaDice(String estado) {
    return new TransaccionSistecredito(
        ID_TRANSACCION, REFERENCIA.valor(), estado, null, null, null, null);
  }

  private ProcesarNotificacionSistecreditoComando notificacion(String estado) {
    return new ProcesarNotificacionSistecreditoComando(ID_TRANSACCION, REFERENCIA.valor(), estado);
  }

  @Test
  void unaNotificacionAprobadaQueLaPasarelaConfirmaAplicaElPago() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(laPasarelaDice("Approved"));

    assertEquals(
        ResultadoNotificacionSistecredito.APLICADO, caso.ejecutar(notificacion("Approved")));
    assertEquals(EstadoPago.APROBADO, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
    // Con el inventario confirmado el pedido no se queda en PAGADO: sigue de una vez a
    // EN_PREPARACION, igual que con Wompi. Es el aplicador compartido haciendo su trabajo.
    assertEquals(
        EstadoPedido.EN_PREPARACION, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  /**
   * <b>La prueba que justifica todo el diseño.</b> Alguien manda al endpoint público un cuerpo que
   * dice "Approved" para una transacción que la pasarela reporta como rechazada. No se aplica nada.
   */
  @Test
  void unaNotificacionQueMienteSobreElEstadoNoAplicaNada() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(laPasarelaDice("Rejected"));

    assertEquals(
        ResultadoNotificacionSistecredito.DISCREPANCIA_CON_LA_PASARELA,
        caso.ejecutar(notificacion("Approved")));
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
    assertEquals(
        EstadoPedido.PAGO_PENDIENTE, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  /**
   * Una notificación que cita una factura nuestra que no existe no llega ni a preguntar.
   *
   * <p>Eso último es la mitad del asunto: este endpoint es público y anónimo, y antes consultaba a
   * la pasarela <b>antes</b> de mirar si la referencia existía. Cualquiera podía hacernos gastar
   * una llamada con nuestras credenciales productivas por cada petición inventada, cada una
   * reteniendo además una conexión hasta que la pasarela respondiera.
   */
  @Test
  void unaNotificacionInventadaNoLlegaAPreguntarle() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(laPasarelaDice("Approved"));

    // Ni la transaccion ni la factura son de ningun pago nuestro: es lo que manda quien sondea el
    // endpoint, que es publico y anonimo. No se gasta una consulta con credenciales productivas.
    assertEquals(
        ResultadoNotificacionSistecredito.PAGO_NO_ENCONTRADO,
        caso.ejecutar(
            new ProcesarNotificacionSistecreditoComando(
                "000000000000000000000000", "TS-2026-000999-1", "Approved")));
    assertEquals(0, pasarela.consultas());
  }

  /**
   * Y el caso que de verdad se recorre en produccion: <b>sin factura en el cuerpo</b>, que es como
   * llegan las notificaciones de verdad (medido el 23 de septiembre de 2026). Antes este era el
   * unico camino y era justo el que preguntaba antes de mirar nada local, o sea que la proteccion
   * que el comentario de la clase describia no existia en produccion.
   */
  @Test
  void unaNotificacionSinFacturaYConUnaTransaccionAjenaTampocoLlegaAPreguntarle() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(laPasarelaDice("Approved"));

    assertEquals(
        ResultadoNotificacionSistecredito.PAGO_NO_ENCONTRADO,
        caso.ejecutar(
            new ProcesarNotificacionSistecreditoComando(
                "000000000000000000000000", null, "Approved")));
    assertEquals(0, pasarela.consultas());
  }

  /**
   * El id de transacción también tiene que ser el que ese pago guardó. Decía "si no tengo id
   * guardado, acepta cualquiera", y un pago de otra pasarela cuyo id nunca se registró —el
   * comprador que cierra la pestaña— cumplía la condición.
   */
  @Test
  void unaNotificacionConOtroIdDeTransaccionNoAplicaNada() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(
        new TransaccionSistecredito(
            "id-de-otra", REFERENCIA.valor(), "Approved", null, null, null, null));

    assertEquals(
        ResultadoNotificacionSistecredito.DISCREPANCIA_CON_LA_PASARELA,
        caso.ejecutar(
            new ProcesarNotificacionSistecreditoComando(
                "id-de-otra", REFERENCIA.valor(), "Approved")));
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
  }

  /**
   * De ida el monto lo pone el servidor; de vuelta no había nada que lo comprobara. Un crédito
   * aprobado por menos de lo pedido —un cupo tope— se habría aplicado como pago completo y la
   * diferencia sería pérdida invisible.
   */
  @Test
  void unMontoAprobadoDistintoDelDelPedidoNoSeAplica() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(
        new TransaccionSistecredito(
            ID_TRANSACCION, REFERENCIA.valor(), "Approved", 50_000L, null, null, null));

    assertEquals(
        ResultadoNotificacionSistecredito.MONTO_NO_COINCIDE,
        caso.ejecutar(notificacion("Approved")));
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
  }

  /**
   * Un segundo estado terminal distinto —`Rejected` y después `Expired`— son dos ids de evento con
   * el mismo `EstadoPago`. Reventaba con una excepción de dominio que salía como 422, justo lo que
   * el controlador promete no devolver nunca, y la pasarela habría reintentado en bucle.
   */
  @Test
  void unSegundoEstadoTerminalNoRevienta() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(laPasarelaDice("Rejected"));
    assertEquals(
        ResultadoNotificacionSistecredito.APLICADO, caso.ejecutar(notificacion("Rejected")));

    pasarela.responder(laPasarelaDice("Expired"));

    assertEquals(
        ResultadoNotificacionSistecredito.YA_PROCESADO, caso.ejecutar(notificacion("Expired")));
  }

  /** La grafía exacta del estado no está garantizada: las guías son de 2023 y no hay sandbox. */
  @Test
  void elEstadoSeEntiendeSinImportarLasMayusculas() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(laPasarelaDice("APPROVED"));

    assertEquals(
        ResultadoNotificacionSistecredito.APLICADO, caso.ejecutar(notificacion("APPROVED")));
    assertEquals(EstadoPago.APROBADO, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
  }

  /**
   * Sin poder preguntar no se aplica. Cuesta un retraso de minutos —la conciliación lo recoge— y
   * evita el único fallo caro: creerle a un cuerpo que nadie verificó.
   */
  @Test
  void siNoSePuedePreguntarALaPasarelaNoSeAplicaNada() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(null);

    assertEquals(
        ResultadoNotificacionSistecredito.NO_SE_PUDO_VERIFICAR,
        caso.ejecutar(notificacion("Approved")));
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
  }

  @Test
  void sinIdDeTransaccionNoHayNadaQueVerificar() {
    ProcesarNotificacionSistecredito caso = crear();

    assertEquals(
        ResultadoNotificacionSistecredito.NO_SE_PUDO_VERIFICAR,
        caso.ejecutar(
            new ProcesarNotificacionSistecreditoComando(null, REFERENCIA.valor(), "Approved")));
  }

  /** La pasarela avisa cada cambio de estado, y el camino no es un resultado. */
  @Test
  void losEstadosEnVueloNoSonUnResultado() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(laPasarelaDice("PendingForPaymentMethod"));

    assertEquals(
        ResultadoNotificacionSistecredito.ESTADO_NO_SOPORTADO,
        caso.ejecutar(notificacion("PendingForPaymentMethod")));
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
  }

  /** Cancelada, vencida o abandonada: la compra no ocurrió y la reserva tiene que soltarse. */
  @Test
  void unaTransaccionAbandonadaRechazaElPago() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(laPasarelaDice("Abandoned"));

    assertEquals(
        ResultadoNotificacionSistecredito.APLICADO, caso.ejecutar(notificacion("Abandoned")));
    assertEquals(
        EstadoPago.RECHAZADO, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
  }

  /** Se repite la misma notificación: la segunda no vuelve a mover nada. */
  @Test
  void laMismaNotificacionDosVecesSoloSeAplicaUnaVez() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(laPasarelaDice("Approved"));

    assertEquals(
        ResultadoNotificacionSistecredito.APLICADO, caso.ejecutar(notificacion("Approved")));
    assertEquals(
        ResultadoNotificacionSistecredito.YA_PROCESADO, caso.ejecutar(notificacion("Approved")));
  }

  /**
   * Nuestra transaccion, pero la pasarela dice que esa transaccion es de <b>otra factura</b>. No es
   * "pago no encontrado" —el pago esta, y es el que registro ese id— sino una discrepancia: lo que
   * el tercero tiene guardado no cuadra con lo nuestro, y eso se registra como error y no aplica
   * nada.
   *
   * <p>Esta comprobacion faltaba y no se notaba: el pago se buscaba por la referencia del cuerpo,
   * asi que una factura ajena simplemente no encontraba nada. Buscando por id de transaccion —que
   * es lo que evita gastar una consulta por peticion anonima— si encuentra, y entonces el contraste
   * tiene que ser contra lo guardado. `coincide` no sirve para eso: compara la pasarela contra el
   * cuerpo, que lo escribe quien manda la peticion.
   */
  @Test
  void unaFacturaAjenaSobreNuestraTransaccionEsUnaDiscrepancia() {
    ProcesarNotificacionSistecredito caso = crear();
    pasarela.responder(
        new TransaccionSistecredito(
            ID_TRANSACCION, "TS-2026-000777-1", "Approved", null, null, null, null));

    assertEquals(
        ResultadoNotificacionSistecredito.DISCREPANCIA_CON_LA_PASARELA,
        caso.ejecutar(
            new ProcesarNotificacionSistecreditoComando(
                ID_TRANSACCION, "TS-2026-000777-1", "Approved")));
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(REFERENCIA).orElseThrow().estado());
  }
}
