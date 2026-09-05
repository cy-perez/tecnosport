package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
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

class ConciliarTransferenciaTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;
  private RepositorioInventarioFalso inventarios;

  private ConciliarTransferencia crear() {
    pedidos = new RepositorioPedidosFalso();
    inventarios = new RepositorioInventarioFalso();
    return new ConciliarTransferencia(pedidos, inventarios, new RelojFalso(AHORA));
  }

  private Pedido pedidoConMetodo(MetodoPago metodoPago, UUID varianteId, UUID idReserva) {
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
            DIRECCION_MEDELLIN,
            metodoPago,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  /** Reserva real, vigente, en un inventario que sí queda registrado en el repositorio falso. */
  private Pedido pedidoConReservaVigente(MetodoPago metodoPago) {
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(1, "stock inicial de prueba", AHORA);
    MovimientoInventario reserva = inventario.reservar(1, Duration.ofMinutes(30), AHORA);
    inventarios.conInventario(inventario);
    return pedidoConMetodo(metodoPago, varianteId, reserva.id());
  }

  @Test
  void conciliaUnPedidoDeTransferenciaManualPendienteYLoDejaListoParaPreparar() {
    ConciliarTransferencia caso = crear();
    Pedido pedido = pedidoConReservaVigente(MetodoPago.TRANSFERENCIA_MANUAL);

    Pedido conciliado = caso.ejecutar(new ConciliarTransferenciaComando(pedido.id(), "admin:test"));

    // Encadena PAGADO -> EN_PREPARACION solo porque la reserva de inventario sí se pudo confirmar.
    assertEquals(EstadoPedido.EN_PREPARACION, conciliado.estado());
    assertEquals(3, conciliado.historial().size());
    assertEquals(EstadoPedido.PAGADO, conciliado.historial().get(1).estado());
    assertEquals(EstadoPedido.EN_PREPARACION, conciliado.historial().get(2).estado());
    assertEquals("admin:test", conciliado.historial().get(2).actor());
  }

  @Test
  void siLaReservaYaVencioElPedidoQuedaEnPagadoParaRevisionManual() {
    ConciliarTransferencia caso = crear();
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(1, "stock inicial de prueba", AHORA.minus(Duration.ofHours(2)));
    // Vence antes de AHORA: al conciliar, la unidad pudo haberse vendido a otro comprador.
    MovimientoInventario reservaVencida =
        inventario.reservar(1, Duration.ofMinutes(30), AHORA.minus(Duration.ofHours(1)));
    inventarios.conInventario(inventario);
    Pedido pedido =
        pedidoConMetodo(MetodoPago.TRANSFERENCIA_MANUAL, varianteId, reservaVencida.id());

    Pedido conciliado = caso.ejecutar(new ConciliarTransferenciaComando(pedido.id(), "admin:test"));

    assertEquals(EstadoPedido.PAGADO, conciliado.estado());
    assertEquals(2, conciliado.historial().size());
  }

  @Test
  void siNoHayInventarioParaLaVarianteElPedidoQuedaEnPagado() {
    ConciliarTransferencia caso = crear();
    // Ninguna variante registrada en el repositorio de inventario falso.
    Pedido pedido =
        pedidoConMetodo(MetodoPago.TRANSFERENCIA_MANUAL, UUID.randomUUID(), UUID.randomUUID());

    Pedido conciliado = caso.ejecutar(new ConciliarTransferenciaComando(pedido.id(), "admin:test"));

    assertEquals(EstadoPedido.PAGADO, conciliado.estado());
  }

  @Test
  void unPedidoDeWompiSeRechaza() {
    ConciliarTransferencia caso = crear();
    Pedido pedido = pedidoConReservaVigente(MetodoPago.NEQUI);

    assertThrows(
        MetodoDePagoNoEsTransferenciaManualException.class,
        () -> caso.ejecutar(new ConciliarTransferenciaComando(pedido.id(), "admin:test")));
  }

  @Test
  void unPedidoInexistenteLanzaPedidoNoEncontrado() {
    ConciliarTransferencia caso = crear();

    assertThrows(
        PedidoNoEncontradoException.class,
        () -> caso.ejecutar(new ConciliarTransferenciaComando(UUID.randomUUID(), "admin:test")));
  }

  @Test
  void unPedidoYaConciliadoNoSePuedeConciliarDeNuevo() {
    ConciliarTransferencia caso = crear();
    Pedido pedido = pedidoConReservaVigente(MetodoPago.TRANSFERENCIA_MANUAL);
    caso.ejecutar(new ConciliarTransferenciaComando(pedido.id(), "admin:test"));

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () -> caso.ejecutar(new ConciliarTransferenciaComando(pedido.id(), "admin:test")));
  }
}
