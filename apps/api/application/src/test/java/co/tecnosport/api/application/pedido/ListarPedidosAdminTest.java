package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListarPedidosAdminTest {

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;

  private ListarPedidosAdmin crear() {
    pedidos = new RepositorioPedidosFalso();
    return new ListarPedidosAdmin(pedidos);
  }

  private Pedido pedido(int secuencial, Instant creadoEn) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, secuencial),
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
            MetodoPago.NEQUI,
            "cliente@tecnosport.co",
            creadoEn);
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void listaPedidosOrdenadosPorFechaDescendente() {
    ListarPedidosAdmin caso = crear();
    Instant t0 = Instant.parse("2026-09-01T00:00:00Z");
    Pedido primero = pedido(1, t0);
    Pedido segundo = pedido(2, t0.plusSeconds(60));

    PedidosPaginados resultado = caso.ejecutar(new ListarPedidosAdminComando(0, 10));

    assertEquals(2, resultado.items().size());
    assertEquals(segundo.id(), resultado.items().get(0).id());
    assertEquals(primero.id(), resultado.items().get(1).id());
    assertEquals(1, resultado.totalPaginas());
    assertEquals(2, resultado.totalPedidos());
  }

  @Test
  void respetaElTamanoDePaginaYCalculaElTotalDePaginas() {
    ListarPedidosAdmin caso = crear();
    Instant t0 = Instant.parse("2026-09-01T00:00:00Z");
    for (int i = 1; i <= 5; i++) {
      pedido(i, t0.plusSeconds(i));
    }

    PedidosPaginados primeraPagina = caso.ejecutar(new ListarPedidosAdminComando(0, 2));
    PedidosPaginados segundaPagina = caso.ejecutar(new ListarPedidosAdminComando(1, 2));

    assertEquals(2, primeraPagina.items().size());
    assertEquals(2, segundaPagina.items().size());
    assertEquals(3, primeraPagina.totalPaginas());
    assertEquals(5, primeraPagina.totalPedidos());
  }

  @Test
  void unaPaginaFueraDeRangoDevuelveVacio() {
    ListarPedidosAdmin caso = crear();
    pedido(1, Instant.parse("2026-09-01T00:00:00Z"));

    PedidosPaginados resultado = caso.ejecutar(new ListarPedidosAdminComando(5, 10));

    assertTrue(resultado.items().isEmpty());
  }

  @Test
  void unTamanoDePaginaFueraDeRangoSeRechaza() {
    assertThrows(IllegalArgumentException.class, () -> new ListarPedidosAdminComando(0, 0));
    assertThrows(IllegalArgumentException.class, () -> new ListarPedidosAdminComando(0, 101));
    assertThrows(IllegalArgumentException.class, () -> new ListarPedidosAdminComando(-1, 10));
  }
}
