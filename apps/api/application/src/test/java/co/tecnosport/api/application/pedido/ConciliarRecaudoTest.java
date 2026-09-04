package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.envio.EnvioNoEncontradoException;
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

class ConciliarRecaudoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;
  private RepositorioEnviosFalso envios;

  private ConciliarRecaudo crear() {
    pedidos = new RepositorioPedidosFalso();
    envios = new RepositorioEnviosFalso();
    return new ConciliarRecaudo(pedidos, envios, new RelojFalso(AHORA));
  }

  private Pedido pedidoConRecaudoPendiente() {
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
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despachado", AHORA);
    pedido.transicionar(EstadoPedido.ENTREGADO, "admin:test", "entregado", AHORA);
    pedido.transicionar(EstadoPedido.RECAUDO_PENDIENTE, "admin:test", "recaudo pendiente", AHORA);
    pedidos.guardar(pedido);
    envios.guardar(
        Envio.crear(pedido.id(), "Servientrega", "SE123456", Dinero.deCop(15_000), AHORA));
    return pedido;
  }

  @Test
  void conciliaElRecaudoYRegistraLaComisionEnElEnvio() {
    ConciliarRecaudo caso = crear();
    Pedido pedido = pedidoConRecaudoPendiente();

    Pedido conciliado =
        caso.ejecutar(new ConciliarRecaudoComando(pedido.id(), Dinero.deCop(5_000), "admin:test"));

    assertEquals(EstadoPedido.RECAUDO_CONCILIADO, conciliado.estado());
    Envio envio = envios.buscarPorPedidoId(pedido.id()).orElseThrow();
    assertEquals(Dinero.deCop(5_000), envio.comisionRecaudo().orElseThrow());
    assertEquals(AHORA, envio.recaudoConciliadoEn().orElseThrow());
  }

  @Test
  void unPedidoInexistenteLanzaPedidoNoEncontrado() {
    ConciliarRecaudo caso = crear();

    assertThrows(
        PedidoNoEncontradoException.class,
        () ->
            caso.ejecutar(
                new ConciliarRecaudoComando(UUID.randomUUID(), Dinero.deCop(5_000), "admin:test")));
  }

  @Test
  void unPedidoQueNoEstaEnRecaudoPendienteNoSePuedeConciliar() {
    ConciliarRecaudo caso = crear();
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
        () ->
            caso.ejecutar(
                new ConciliarRecaudoComando(pedido.id(), Dinero.deCop(5_000), "admin:test")));
  }

  @Test
  void sinEnvioParaElPedidoLanzaEnvioNoEncontrado() {
    ConciliarRecaudo caso = crear();
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 3),
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
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despachado", AHORA);
    pedido.transicionar(EstadoPedido.ENTREGADO, "admin:test", "entregado", AHORA);
    pedido.transicionar(EstadoPedido.RECAUDO_PENDIENTE, "admin:test", "recaudo pendiente", AHORA);
    pedidos.guardar(pedido);

    assertThrows(
        EnvioNoEncontradoException.class,
        () ->
            caso.ejecutar(
                new ConciliarRecaudoComando(pedido.id(), Dinero.deCop(5_000), "admin:test")));
  }
}
