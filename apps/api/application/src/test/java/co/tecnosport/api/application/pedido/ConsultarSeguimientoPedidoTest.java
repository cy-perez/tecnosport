package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
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

class ConsultarSeguimientoPedidoTest {

  private static final Instant AHORA = Instant.parse("2026-09-04T12:00:00Z");

  private RepositorioPedidosFalso pedidos;

  private ConsultarSeguimientoPedido crear() {
    pedidos = new RepositorioPedidosFalso();
    return new ConsultarSeguimientoPedido(pedidos);
  }

  private Pedido pedidoDePrueba(String correo) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            new CorreoElectronico(correo),
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
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            MetodoPago.CONTRAENTREGA,
            correo,
            AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void conElCorreoCorrectoDevuelveElPedido() {
    ConsultarSeguimientoPedido caso = crear();
    Pedido pedido = pedidoDePrueba("cliente@tecnosport.co");

    Pedido encontrado =
        caso.ejecutar(new ConsultarSeguimientoPedidoComando(pedido.id(), "cliente@tecnosport.co"));

    assertEquals(pedido.id(), encontrado.id());
  }

  @Test
  void elCorreoSeComparaSinImportarMayusculasNiEspacios() {
    ConsultarSeguimientoPedido caso = crear();
    Pedido pedido = pedidoDePrueba("cliente@tecnosport.co");

    Pedido encontrado =
        caso.ejecutar(
            new ConsultarSeguimientoPedidoComando(pedido.id(), "  Cliente@TecnoSport.co  "));

    assertEquals(pedido.id(), encontrado.id());
  }

  @Test
  void conElCorreoEquivocadoLanzaPedidoNoEncontrado() {
    ConsultarSeguimientoPedido caso = crear();
    Pedido pedido = pedidoDePrueba("cliente@tecnosport.co");

    assertThrows(
        PedidoNoEncontradoException.class,
        () -> caso.ejecutar(new ConsultarSeguimientoPedidoComando(pedido.id(), "otro@correo.co")));
  }

  @Test
  void unPedidoInexistenteLanzaPedidoNoEncontrado() {
    ConsultarSeguimientoPedido caso = crear();

    assertThrows(
        PedidoNoEncontradoException.class,
        () ->
            caso.ejecutar(
                new ConsultarSeguimientoPedidoComando(UUID.randomUUID(), "cliente@tecnosport.co")));
  }
}
