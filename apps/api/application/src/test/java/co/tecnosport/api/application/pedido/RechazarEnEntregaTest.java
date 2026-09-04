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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RechazarEnEntregaTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;
  private RepositorioInventarioFalso inventarios;
  private UUID varianteId;

  private RechazarEnEntrega crear() {
    pedidos = new RepositorioPedidosFalso();
    inventarios = new RepositorioInventarioFalso();
    return new RechazarEnEntrega(pedidos, inventarios, new RelojFalso(AHORA));
  }

  private Pedido pedidoDespachadoConReservaPendiente() {
    varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(5, "siembra de prueba", AHORA);
    MovimientoInventario reserva = inventario.reservar(1, null, AHORA);
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
                    reserva.id())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", AHORA);
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despachado", AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void rechazaUnPedidoDespachadoYLiberaElInventario() {
    RechazarEnEntrega caso = crear();
    Pedido pedido = pedidoDespachadoConReservaPendiente();
    assertEquals(
        4, inventarios.buscarPorVarianteId(varianteId).orElseThrow().saldoDisponible(AHORA));

    Pedido rechazado =
        caso.ejecutar(
            new RechazarEnEntregaComando(pedido.id(), "cliente no recibió", "admin:test"));

    assertEquals(EstadoPedido.RECHAZADO_EN_ENTREGA, rechazado.estado());
    assertEquals(
        "cliente no recibió", rechazado.historial().get(rechazado.historial().size() - 1).motivo());
    assertEquals(
        5, inventarios.buscarPorVarianteId(varianteId).orElseThrow().saldoDisponible(AHORA));
  }

  @Test
  void unPedidoInexistenteLanzaPedidoNoEncontrado() {
    RechazarEnEntrega caso = crear();

    assertThrows(
        PedidoNoEncontradoException.class,
        () ->
            caso.ejecutar(new RechazarEnEntregaComando(UUID.randomUUID(), "motivo", "admin:test")));
  }

  @Test
  void unPedidoQueNoEstaDespachadoNoSePuedeRechazar() {
    RechazarEnEntrega caso = crear();
    varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(5, "siembra de prueba", AHORA);
    MovimientoInventario reserva = inventario.reservar(1, null, AHORA);
    inventarios.conInventario(inventario);
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 2),
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
                    reserva.id())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.guardar(pedido);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () -> caso.ejecutar(new RechazarEnEntregaComando(pedido.id(), "motivo", "admin:test")));
  }

  @Test
  void unMotivoVacioSeRechaza() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RechazarEnEntregaComando(UUID.randomUUID(), " ", "admin:test"));
  }
}
