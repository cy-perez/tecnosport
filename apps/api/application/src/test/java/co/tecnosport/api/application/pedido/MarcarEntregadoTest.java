package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
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

class MarcarEntregadoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;

  private MarcarEntregado crear() {
    pedidos = new RepositorioPedidosFalso();
    return new MarcarEntregado(pedidos, new RelojFalso(AHORA));
  }

  private Pedido pedidoDespachado(MetodoPago metodoPago) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
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

    Pedido entregado = caso.ejecutar(new MarcarEntregadoComando(pedido.id(), "admin:test"));

    assertEquals(EstadoPedido.RECAUDO_PENDIENTE, entregado.estado());
    assertEquals(5, entregado.historial().size());
  }

  @Test
  void unPedidoPagadoEnLineaEntregadoSeQuedaEnEntregado() {
    MarcarEntregado caso = crear();
    Pedido pedido = pedidoDespachado(MetodoPago.NEQUI);

    Pedido entregado = caso.ejecutar(new MarcarEntregadoComando(pedido.id(), "admin:test"));

    assertEquals(EstadoPedido.ENTREGADO, entregado.estado());
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
