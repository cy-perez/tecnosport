package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.inventario.TipoMovimientoInventario;
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

class MarcarEntregadoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;
  private RepositorioInventarioFalso inventarios;
  private UUID varianteId;
  private UUID idReserva;

  private MarcarEntregado crear() {
    pedidos = new RepositorioPedidosFalso();
    inventarios = new RepositorioInventarioFalso();
    return new MarcarEntregado(pedidos, inventarios, new RelojFalso(AHORA));
  }

  /**
   * Cinco unidades en bodega y una reservada por el pedido, con la vigencia que le corresponde a
   * cada metodo de pago: la de un contraentrega no vence nunca (docs/02-modelo-datos.md), que es
   * justo por lo que podia quedarse abierta para siempre.
   */
  private void inventarioConLaReserva(MetodoPago metodoPago) {
    varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(5, "siembra", AHORA.minusSeconds(1000));
    Duration vigencia = metodoPago == MetodoPago.CONTRAENTREGA ? null : Duration.ofMinutes(30);
    MovimientoInventario reserva = inventario.reservar(1, vigencia, AHORA.minusSeconds(900));
    idReserva = reserva.id();
    if (metodoPago != MetodoPago.CONTRAENTREGA) {
      inventario.confirmar(idReserva, AHORA.minusSeconds(800));
    }
    inventarios.conInventario(inventario);
  }

  private Inventario inventarioDeLaLinea() {
    return inventarios.buscarPorVarianteId(varianteId).orElseThrow();
  }

  private long movimientosDeTipo(TipoMovimientoInventario tipo) {
    return inventarioDeLaLinea().movimientos().stream().filter(m -> m.tipo() == tipo).count();
  }

  private Pedido pedidoDespachado(MetodoPago metodoPago) {
    inventarioConLaReserva(metodoPago);
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
    if (metodoPago == MetodoPago.CONTRAENTREGA) {
      pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", AHORA);
    } else {
      pedido.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "pago aprobado", AHORA);
      pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "en preparación", AHORA);
    }
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despachado", AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void unPedidoContraentregaEntregadoQuedaEnRecaudoPendiente() {
    MarcarEntregado caso = crear();
    Pedido pedido = pedidoDespachado(MetodoPago.CONTRAENTREGA);

    ResultadoEntrega resultado =
        caso.ejecutar(new MarcarEntregadoComando(pedido.id(), "admin:test"));

    assertEquals(EstadoPedido.RECAUDO_PENDIENTE, resultado.pedido().estado());
    assertEquals(5, resultado.pedido().historial().size());
  }

  /**
   * La promesa, no la implementacion: la unidad que el comprador se lleva deja de existir en
   * bodega. Se mira el saldo total y no el disponible — con la reserva abierta el disponible da 4
   * de las dos maneras, y es lo que dejaba pasar el defecto.
   */
  @Test
  void unContraentregaEntregadoSacaLaUnidadDelSaldoTotal() {
    MarcarEntregado caso = crear();
    Pedido pedido = pedidoDespachado(MetodoPago.CONTRAENTREGA);
    assertEquals(5, inventarioDeLaLinea().saldoTotal());

    ResultadoEntrega resultado =
        caso.ejecutar(new MarcarEntregadoComando(pedido.id(), "admin:test"));

    assertTrue(resultado.inventarioConfirmado());
    assertEquals(4, inventarioDeLaLinea().saldoTotal(), "saldo total");
    assertEquals(4, inventarioDeLaLinea().saldoDisponible(AHORA), "saldo disponible");
    assertEquals(1, movimientosDeTipo(TipoMovimientoInventario.SALIDA), "salidas");
  }

  /**
   * La entrega ocurrio de verdad: negarle la transicion al pedido no devuelve la mercancia. Se
   * registra igual y la discrepancia sale por el resultado, mismo criterio que ADR-0014 con un pago
   * aprobado tarde.
   */
  @Test
  void unaReservaYaResueltaNoImpideRegistrarLaEntrega() {
    MarcarEntregado caso = crear();
    Pedido pedido = pedidoDespachado(MetodoPago.CONTRAENTREGA);
    Inventario inventario = inventarioDeLaLinea();
    inventario.liberar(idReserva, "vencida a mano", AHORA.minusSeconds(10));
    inventarios.conInventario(inventario);

    ResultadoEntrega resultado =
        caso.ejecutar(new MarcarEntregadoComando(pedido.id(), "admin:test"));

    assertEquals(EstadoPedido.RECAUDO_PENDIENTE, resultado.pedido().estado());
    assertFalse(resultado.inventarioConfirmado());
    assertEquals(0, movimientosDeTipo(TipoMovimientoInventario.SALIDA), "salidas");
  }

  @Test
  void unPedidoPagadoEnLineaEntregadoSeQuedaEnEntregado() {
    MarcarEntregado caso = crear();
    Pedido pedido = pedidoDespachado(MetodoPago.NEQUI);

    ResultadoEntrega resultado =
        caso.ejecutar(new MarcarEntregadoComando(pedido.id(), "admin:test"));

    assertEquals(EstadoPedido.ENTREGADO, resultado.pedido().estado());
  }

  /** Su reserva se confirmo cuando entro el dinero, mucho antes: entregar no la toca otra vez. */
  @Test
  void unPedidoPagadoEnLineaNoVuelveAConfirmarSuReserva() {
    MarcarEntregado caso = crear();
    Pedido pedido = pedidoDespachado(MetodoPago.NEQUI);

    ResultadoEntrega resultado =
        caso.ejecutar(new MarcarEntregadoComando(pedido.id(), "admin:test"));

    assertTrue(resultado.inventarioConfirmado());
    assertEquals(4, inventarioDeLaLinea().saldoTotal(), "saldo total");
    assertEquals(1, movimientosDeTipo(TipoMovimientoInventario.SALIDA), "salidas");
  }

  @Test
  void unPedidoInexistenteLanzaPedidoNoEncontrado() {
    MarcarEntregado caso = crear();

    assertThrows(
        PedidoNoEncontradoException.class,
        () -> caso.ejecutar(new MarcarEntregadoComando(UUID.randomUUID(), "admin:test")));
  }

  @Test
  void unPedidoQueNoEstaDespachadoNoSePuedeMarcarEntregado() {
    MarcarEntregado caso = crear();
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 2),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.guardar(pedido);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () -> caso.ejecutar(new MarcarEntregadoComando(pedido.id(), "admin:test")));
  }
}
