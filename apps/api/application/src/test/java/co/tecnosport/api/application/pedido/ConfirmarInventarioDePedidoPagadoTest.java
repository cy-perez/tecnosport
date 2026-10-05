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

/** La salida del pedido pagado que se quedó sin inventario confirmado. */
class ConfirmarInventarioDePedidoPagadoTest {

  private static final Instant AHORA = Instant.parse("2026-10-04T12:00:00Z");

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private UUID varianteId;

  private ConfirmarInventarioDePedidoPagado casoDeUso() {
    return new ConfirmarInventarioDePedidoPagado(pedidos, inventarios, new RelojFalso(AHORA));
  }

  /** Pagado ayer con la reserva ya vencida, y la unidad vendida a otro mientras tanto. */
  private Pedido pagadoSinExistencia(MetodoPago metodo) {
    varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(1, "siembra", AHORA.minus(Duration.ofDays(2)));
    MovimientoInventario reserva =
        inventario.reservar(1, Duration.ofMinutes(30), AHORA.minus(Duration.ofDays(1)));
    inventario.reservar(1, null, AHORA.minus(Duration.ofHours(20)));
    inventarios.conInventario(inventario);
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 11),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta",
                    1,
                    Dinero.deCop(50_000),
                    BigDecimal.ZERO,
                    null,
                    reserva.id())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            metodo,
            "cliente@tecnosport.co",
            AHORA.minus(Duration.ofDays(1)));
    if (metodo != MetodoPago.CONTRAENTREGA) {
      pedido.transicionar(EstadoPedido.PAGADO, "webhook", "aprobado", AHORA.minusSeconds(60));
    }
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void sinExistenciaNoCambiaNadaYLoDice() {
    Pedido pedido = pagadoSinExistencia(MetodoPago.WOMPI);

    assertThrows(
        InventarioSinConfirmarException.class, () -> casoDeUso().ejecutar(pedido.id(), "admin:1"));
    assertEquals(EstadoPedido.PAGADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  /** Llegó mercancía: el mismo pedido ahora sí se prepara. */
  @Test
  void conLaReposicionPasaAPreparacion() {
    Pedido pedido = pagadoSinExistencia(MetodoPago.WOMPI);
    Inventario inventario = inventarios.buscarPorVarianteId(varianteId).orElseThrow();
    inventario.registrarEntrada(3, "reposición", AHORA);
    inventarios.guardar(inventario);

    Pedido preparado = casoDeUso().ejecutar(pedido.id(), "admin:1");

    assertEquals(EstadoPedido.EN_PREPARACION, preparado.estado());
    assertEquals(3, inventarios.buscarPorVarianteId(varianteId).orElseThrow().saldoTotal());
  }

  /** Un contraentrega confirmado no está pagado: esto no puede sacar mercancía sin dinero. */
  @Test
  void unPedidoQueNoEstaPagadoNoSePreparaPorAqui() {
    Pedido pedido = pagadoSinExistencia(MetodoPago.CONTRAENTREGA);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () -> casoDeUso().ejecutar(pedido.id(), "admin:1"));
    assertEquals(
        EstadoPedido.CONFIRMADO_CONTRAENTREGA,
        pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }
}
