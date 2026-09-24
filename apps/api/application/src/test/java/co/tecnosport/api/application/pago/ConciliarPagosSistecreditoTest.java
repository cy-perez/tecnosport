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
 * La red por debajo de la notificación: si el comprador cierra la ventana, o si la notificación
 * llegó y no se pudo verificar, el pago se queda esperando esto ({@code adr/0048}).
 */
class ConciliarPagosSistecreditoTest {

  private static final Instant AHORA = Instant.parse("2026-09-20T12:00:00Z");
  private static final Instant HACE_UNA_HORA = AHORA.minus(Duration.ofHours(1));
  private static final CorreoElectronico CORREO = new CorreoElectronico("cliente@tecnosport.co");
  private static final Direccion DIRECCION =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPagosFalso pagos;
  private RepositorioPedidosFalso pedidos;
  private RepositorioInventarioFalso inventarios;
  private PasarelaSistecreditoFalsa pasarela;

  private ConciliarPagosSistecredito crear() {
    pagos = new RepositorioPagosFalso();
    pedidos = new RepositorioPedidosFalso();
    inventarios = new RepositorioInventarioFalso();
    pasarela = new PasarelaSistecreditoFalsa();
    return new ConciliarPagosSistecredito(
        pagos, pedidos, inventarios, pasarela, new RelojFalso(AHORA), Duration.ofMinutes(5));
  }

  private Pago pagoPendienteCon(MetodoPago metodoPago, String idTransaccion, int secuencial) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, secuencial),
            null,
            CORREO,
            List.of(linea()),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION,
            metodoPago,
            "cliente@tecnosport.co",
            HACE_UNA_HORA);
    pedidos.conPedido(pedido);
    Pago pago =
        Pago.crear(
            pedido.id(),
            new ReferenciaPago(pedido.numeroPedido().valor() + "-1"),
            metodoPago,
            pedido.total(),
            HACE_UNA_HORA);
    pago.registrarIdTransaccionPasarela(idTransaccion);
    pagos.guardar(pago);
    return pago;
  }

  private LineaPedido linea() {
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(10, "siembra de prueba", HACE_UNA_HORA);
    MovimientoInventario reserva = inventario.reservar(1, Duration.ofHours(6), HACE_UNA_HORA);
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

  @Test
  void resuelveUnPagoQueSeQuedoPendienteSinNotificacion() {
    ConciliarPagosSistecredito caso = crear();
    Pago pago = pagoPendienteCon(MetodoPago.SISTECREDITO, "id-sistecredito", 1);
    pasarela.responder(
        new TransaccionSistecredito(
            "id-sistecredito", pago.referencia().valor(), "Approved", null, null, null, null));

    ResultadoConciliacion resultado = caso.ejecutar();

    assertEquals(1, resultado.revisados());
    assertEquals(1, resultado.conciliados());
    assertEquals(
        EstadoPago.APROBADO, pagos.buscarPorReferencia(pago.referencia()).orElseThrow().estado());
  }

  /**
   * <b>La prueba que justifica el filtro.</b> La columna del id de transacción es la misma para las
   * dos pasarelas, así que esta consulta devuelve también los pagos de Wompi. Preguntarle a
   * Sistecrédito por un id de Wompi no rompería nada visible —respondería que no existe— y ese es
   * justo el problema: parecería que alguien los está conciliando.
   */
  @Test
  void noTocaLosPagosDeLaOtraPasarela() {
    ConciliarPagosSistecredito caso = crear();
    pagoPendienteCon(MetodoPago.NEQUI, "id-de-wompi", 2);

    ResultadoConciliacion resultado = caso.ejecutar();

    assertEquals(0, resultado.revisados());
  }

  /** Sigue en vuelo: no es un fallo, la próxima corrida lo vuelve a mirar. */
  @Test
  void unaTransaccionTodaviaEnVueloNoSeCuentaComoConciliada() {
    ConciliarPagosSistecredito caso = crear();
    Pago pago = pagoPendienteCon(MetodoPago.SISTECREDITO, "id-sistecredito", 1);
    pasarela.responder(
        new TransaccionSistecredito(
            "id-sistecredito", pago.referencia().valor(), "Pending", null, null, null, null));

    ResultadoConciliacion resultado = caso.ejecutar();

    assertEquals(1, resultado.revisados());
    assertEquals(0, resultado.conciliados());
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(pago.referencia()).orElseThrow().estado());
  }

  /**
   * El caso del cupo tope, que es justo lo que hace un prestamista: aprueba por menos de lo pedido.
   * {@code ProcesarNotificacionSistecredito} ya lo rechazaba por el camino de la notificación, con
   * el motivo escrito —"la diferencia sería pérdida invisible"—; este camino, que es el que se
   * recorre cuando el comprador cierra la ventana, lo aplicaba como pago completo.
   */
  @Test
  void unCreditoAprobadoPorMenosDeLoPedidoNoSeConcilia() {
    ConciliarPagosSistecredito caso = crear();
    Pago pago = pagoPendienteCon(MetodoPago.SISTECREDITO, "id-sistecredito", 1);
    long dosTercios = pago.monto().valor().longValueExact() * 2 / 3;
    pasarela.responder(
        new TransaccionSistecredito(
            "id-sistecredito",
            pago.referencia().valor(),
            "Approved",
            dosTercios,
            null,
            null,
            null));

    ResultadoConciliacion resultado = caso.ejecutar();

    assertEquals(1, resultado.revisados());
    assertEquals(0, resultado.conciliados());
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(pago.referencia()).orElseThrow().estado());
  }

  /**
   * El id de transacción lo estampa un endpoint público y anónimo, igual que en Wompi. Si la
   * factura que la pasarela reporta no es la nuestra, la transacción es de otra compra.
   */
  @Test
  void unaTransaccionAprobadaDeOtraFacturaNoSeConcilia() {
    ConciliarPagosSistecredito caso = crear();
    Pago pago = pagoPendienteCon(MetodoPago.SISTECREDITO, "id-sistecredito", 1);
    pasarela.responder(
        new TransaccionSistecredito(
            "id-sistecredito",
            "TS-2026-000999-1",
            "Approved",
            pago.monto().valor().longValueExact(),
            null,
            null,
            null));

    ResultadoConciliacion resultado = caso.ejecutar();

    assertEquals(1, resultado.revisados());
    assertEquals(0, resultado.conciliados());
    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(pago.referencia()).orElseThrow().estado());
  }

  /**
   * Si la conciliación resuelve el pago y la notificación llega después, el agregado la reconoce
   * como repetida porque los dos caminos componen el mismo id de evento. Sin eso, la notificación
   * tardía intentaría aplicar un estado sobre un pago ya final.
   */
  @Test
  void laNotificacionTardiaNoVuelveAAplicarLoQueLaConciliacionYaResolvio() {
    ConciliarPagosSistecredito caso = crear();
    Pago pago = pagoPendienteCon(MetodoPago.SISTECREDITO, "id-sistecredito", 1);
    TransaccionSistecredito aprobada =
        new TransaccionSistecredito(
            "id-sistecredito", pago.referencia().valor(), "Approved", null, null, null, null);
    pasarela.responder(aprobada);
    caso.ejecutar();

    ProcesarNotificacionSistecredito notificacion =
        new ProcesarNotificacionSistecredito(
            pagos, pedidos, inventarios, pasarela, new RelojFalso(AHORA));
    ResultadoNotificacionSistecredito resultado =
        notificacion.ejecutar(
            new ProcesarNotificacionSistecreditoComando(
                "id-sistecredito", pago.referencia().valor(), "Approved"));

    assertEquals(ResultadoNotificacionSistecredito.YA_PROCESADO, resultado);
  }
}
