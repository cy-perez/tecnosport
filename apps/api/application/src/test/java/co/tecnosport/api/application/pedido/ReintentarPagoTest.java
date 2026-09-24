package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.inventario.ExistenciaInsuficienteException;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReintentarPagoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration RESERVA_PAGO_EN_LINEA = Duration.ofMinutes(30);
  private static final Direccion DIRECCION_MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;
  private RepositorioInventarioFalso inventarios;
  private UUID varianteId;

  private ReintentarPago crear() {
    pedidos = new RepositorioPedidosFalso();
    inventarios = new RepositorioInventarioFalso();
    return new ReintentarPago(pedidos, inventarios, new RelojFalso(AHORA), RESERVA_PAGO_EN_LINEA);
  }

  private Pedido pedidoFallidoConInventario(int existencia) {
    varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    if (existencia > 0) {
      inventario.registrarEntrada(existencia, "siembra de prueba", AHORA);
    }
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
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.NEQUI,
            "cliente@tecnosport.co",
            AHORA);
    pedido.transicionar(EstadoPedido.PAGO_FALLIDO, "webhook-wompi", "pago rechazado", AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void unPedidoConPagoFallidoVuelveAPagoPendienteConUnaReservaNueva() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);
    UUID reservaOriginal = pedido.lineas().get(0).idReserva();

    Pedido reintentado =
        caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co"));

    assertEquals(EstadoPedido.PAGO_PENDIENTE, reintentado.estado());
    assertEquals(3, reintentado.historial().size());
    assertEquals("cliente@tecnosport.co", reintentado.historial().get(2).actor());
    assertNotEquals(reservaOriginal, reintentado.lineas().get(0).idReserva());
  }

  @Test
  void reintentarReservaDeNuevoLaExistenciaDisponible() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);

    caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co"));

    Inventario inventario = inventarios.buscarPorVarianteId(varianteId).orElseThrow();
    assertEquals(4, inventario.saldoDisponible(AHORA));
  }

  @Test
  void reintentarSinExistenciaSuficienteLanzaExistenciaInsuficiente() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(0);

    assertThrows(
        ExistenciaInsuficienteException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co")));
  }

  @Test
  void unPedidoInexistenteLanzaPedidoNoEncontrado() {
    ReintentarPago caso = crear();

    assertThrows(
        PedidoNoEncontradoException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(UUID.randomUUID(), "cliente@tecnosport.co")));
  }

  /**
   * El correo autoriza, igual que en el seguimiento. Sin esto, cualquiera con el id del pedido —que
   * viaja en la URL de retorno de la pasarela y en el correo de confirmación— podía volver a
   * reservar inventario y leerse el pedido entero: correo, teléfono, dirección e historial.
   *
   * <p>Y la respuesta es 404 y no 403 a propósito: un 403 confirmaría que ese id corresponde a una
   * compra real, que es justo lo que no hay que confirmarle a quien prueba identificadores.
   */
  @Test
  void unCorreoQueNoEsElDelPedidoSeTrataComoSiElPedidoNoExistiera() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);
    UUID reservaOriginal = pedido.lineas().get(0).idReserva();

    assertThrows(
        PedidoNoEncontradoException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "otro@tecnosport.co")));

    // Y no tocó nada: ni el estado, ni la reserva.
    assertEquals(EstadoPedido.PAGO_FALLIDO, pedido.estado());
    assertEquals(reservaOriginal, pedido.lineas().get(0).idReserva());
  }

  /** El correo se normaliza igual que en el seguimiento: espacios y mayúsculas no descalifican. */
  @Test
  void elCorreoAutorizaAunqueVengaConEspaciosYEnMayusculas() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);

    Pedido reintentado =
        caso.ejecutar(new ReintentarPagoComando(pedido.id(), "  CLIENTE@TecnoSport.CO  "));

    assertEquals(EstadoPedido.PAGO_PENDIENTE, reintentado.estado());
  }

  @Test
  void unPedidoQueNoEstaEnPagoFallidoNoSePuedeReintentarNiReservaNada() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);
    pedido.transicionar(
        EstadoPedido.PAGO_PENDIENTE, "cliente@tecnosport.co", "ya reintentado", AHORA);
    pedidos.guardar(pedido);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co")));

    // La transición inválida se detecta antes de reservar: el intento fallido no dejó ninguna
    // reserva nueva, el disponible sigue en la existencia completa.
    Inventario inventario = inventarios.buscarPorVarianteId(varianteId).orElseThrow();
    assertEquals(5, inventario.saldoDisponible(AHORA));
  }
}
