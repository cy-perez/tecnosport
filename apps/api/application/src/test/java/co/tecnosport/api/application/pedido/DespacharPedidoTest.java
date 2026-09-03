package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.Envio;
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

class DespacharPedidoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;
  private RepositorioEnviosFalso envios;

  private DespacharPedido crear() {
    pedidos = new RepositorioPedidosFalso();
    envios = new RepositorioEnviosFalso();
    return new DespacharPedido(pedidos, envios, new RelojFalso(AHORA));
  }

  private Pedido pedidoEnPreparacion() {
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
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  private DespacharPedidoComando comando(UUID pedidoId) {
    return new DespacharPedidoComando(
        pedidoId, "Servientrega", "SE123456", Dinero.deCop(15_000), "admin:test");
  }

  @Test
  void despachaUnPedidoEnPreparacion() {
    DespacharPedido caso = crear();
    Pedido pedido = pedidoEnPreparacion();

    Pedido despachado = caso.ejecutar(comando(pedido.id()));

    assertEquals(EstadoPedido.DESPACHADO, despachado.estado());
    assertEquals(3, despachado.historial().size());
    assertEquals(1, envios.guardados().size());
    Envio envio = envios.guardados().get(0);
    assertEquals(pedido.id(), envio.pedidoId());
    assertEquals("Servientrega", envio.transportadora());
    assertEquals("SE123456", envio.guia());
    assertEquals(Dinero.deCop(15_000), envio.costoEnvio());
  }

  @Test
  void unPedidoInexistenteLanzaPedidoNoEncontrado() {
    DespacharPedido caso = crear();

    assertThrows(
        PedidoNoEncontradoException.class, () -> caso.ejecutar(comando(UUID.randomUUID())));
  }

  @Test
  void unPedidoQueNoEstaEnPreparacionNoSePuedeDespacharYNoDejaUnEnvioHuerfano() {
    DespacharPedido caso = crear();
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
        TransicionDeEstadoInvalidaException.class, () -> caso.ejecutar(comando(pedido.id())));
    assertTrue(envios.guardados().isEmpty());
  }
}
