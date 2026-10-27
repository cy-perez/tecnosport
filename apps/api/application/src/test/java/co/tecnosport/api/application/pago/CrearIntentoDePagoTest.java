package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.Pago;
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

class CrearIntentoDePagoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final CorreoElectronico CORREO = new CorreoElectronico("cliente@tecnosport.co");
  private static final Direccion DIRECCION_MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;
  private RepositorioPagosFalso pagos;
  private RepositorioInventarioFalso inventario = new RepositorioInventarioFalso();

  private CrearIntentoDePago crear() {
    pedidos = new RepositorioPedidosFalso();
    pagos = new RepositorioPagosFalso();
    inventario = new RepositorioInventarioFalso();
    return new CrearIntentoDePago(
        pedidos, pagos, new PasarelaDePagosFalsa(), inventario, new RelojFalso(AHORA));
  }

  private LineaPedido linea() {
    return lineaReservadaEn(AHORA);
  }

  /**
   * Una línea con su reserva de 30 minutos hecha en {@code reservadaEn}, como la deja el pedido.
   */
  private LineaPedido lineaReservadaEn(Instant reservadaEn) {
    UUID varianteId = UUID.randomUUID();
    Inventario libro = Inventario.crear(varianteId);
    libro.registrarEntrada(5, "siembra", reservadaEn);
    MovimientoInventario reserva = libro.reservar(2, Duration.ofMinutes(30), reservadaEn);
    inventario.conInventario(libro);
    return new LineaPedido(
        UUID.randomUUID(),
        varianteId,
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        2,
        Dinero.deCop(50_000),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.webp",
        reserva.id());
  }

  private Pedido pedidoConLinea(MetodoPago metodoPago, LineaPedido linea) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 7),
            null,
            CORREO,
            List.of(linea),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            metodoPago,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.conPedido(pedido);
    return pedido;
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
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI, 1);

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
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI, 1);

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

  /**
   * La reserva del pago en línea dura 30 minutos. Un pedido que se quedó en {@code PAGO_PENDIENTE}
   * más que eso ya no tiene la unidad apartada: cobrarlo era dejarlo {@code PAGADO} sin mercancía
   * detrás si otro comprador se la llevó entretanto.
   */
  @Test
  void conLaReservaVencidaNoSeCreaElPago() {
    CrearIntentoDePago caso = crear();
    Pedido pedido =
        pedidoConLinea(MetodoPago.WOMPI, lineaReservadaEn(AHORA.minus(Duration.ofMinutes(31))));

    assertThrows(
        ReservaDelPedidoVencidaException.class,
        () -> caso.ejecutar(new CrearIntentoDePagoComando(pedido.id())));
    assertTrue(pagos.buscarPorPedidoId(pedido.id()).isEmpty());
  }

  @Test
  void conLaReservaLiberadaNoSeCreaElPago() {
    CrearIntentoDePago caso = crear();
    LineaPedido linea = lineaReservadaEn(AHORA);
    Inventario libro = inventario.buscarPorVarianteId(linea.varianteId()).orElseThrow();
    libro.liberar(linea.idReserva(), "otro camino la soltó", AHORA);
    inventario.guardar(libro);
    Pedido pedido = pedidoConLinea(MetodoPago.WOMPI, linea);

    assertThrows(
        ReservaDelPedidoVencidaException.class,
        () -> caso.ejecutar(new CrearIntentoDePagoComando(pedido.id())));
    assertTrue(pagos.buscarPorPedidoId(pedido.id()).isEmpty());
  }
}
