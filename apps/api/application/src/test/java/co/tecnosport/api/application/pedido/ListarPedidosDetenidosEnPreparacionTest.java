package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.EstadoPedido;
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

/** La alerta de pedidos detenidos en preparación: cuenta desde la llegada, no desde la creación. */
class ListarPedidosDetenidosEnPreparacionTest {

  private static final Instant AHORA = Instant.parse("2026-10-27T12:00:00Z");
  private static final Duration UMBRAL = Duration.ofDays(3);

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private int secuencial;

  private ListarPedidosDetenidosEnPreparacion listar() {
    return new ListarPedidosDetenidosEnPreparacion(pedidos, new RelojFalso(AHORA), UMBRAL);
  }

  /** Recogida pagada en el punto: nace confirmada y pasa a preparación en {@code enPreparacion}. */
  private Pedido recogidaEnPreparacionDesde(Instant creado, Instant enPreparacion) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, ++secuencial),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta",
                    1,
                    Dinero.deCop(50_000),
                    BigDecimal.ZERO,
                    null,
                    UUID.randomUUID())),
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            MetodoPago.PAGO_EN_PUNTO,
            "cliente@tecnosport.co",
            creado);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:1", "verificado", enPreparacion);
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void muestraElQueLlevaMasDelUmbralConSuFechaDeLlegada() {
    Instant llegada = AHORA.minus(Duration.ofDays(4));
    Pedido detenido = recogidaEnPreparacionDesde(llegada, llegada);

    PedidosDetenidosEnPreparacion alerta = listar().ejecutar();

    assertEquals(UMBRAL, alerta.umbral());
    assertEquals(1, alerta.pedidos().size());
    assertEquals(detenido.id(), alerta.pedidos().get(0).pedido().id());
    assertEquals(llegada, alerta.pedidos().get(0).desde());
  }

  /** Creado hace una semana, pero en preparación desde ayer: no lleva tres días ahí. */
  @Test
  void cuentaDesdeQueLlegoAPreparacionNoDesdeQueSeCreo() {
    recogidaEnPreparacionDesde(AHORA.minus(Duration.ofDays(7)), AHORA.minus(Duration.ofDays(1)));

    assertTrue(listar().ejecutar().pedidos().isEmpty());
  }

  @Test
  void elQueYaSalioDePreparacionNoSale() {
    Instant llegada = AHORA.minus(Duration.ofDays(4));
    Pedido listo = recogidaEnPreparacionDesde(llegada, llegada);
    listo.transicionar(EstadoPedido.LISTO_PARA_RECOGER, "admin:1", "listo", AHORA);
    pedidos.guardar(listo);

    assertTrue(listar().ejecutar().pedidos().isEmpty());
  }

  @Test
  void unUmbralNoPositivoSeRechazaAlConstruir() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ListarPedidosDetenidosEnPreparacion(pedidos, new RelojFalso(AHORA), Duration.ZERO));
  }
}
