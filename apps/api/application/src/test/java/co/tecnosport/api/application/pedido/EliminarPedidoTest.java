package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Se borra lo que nunca tuvo nada en juego; lo demás se cancela y conserva su historia. */
class EliminarPedidoTest {

  private static final Instant AHORA = Instant.parse("2026-10-10T12:00:00Z");

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final BorradoFalso borrado = new BorradoFalso();
  private final EliminarPedido eliminar = new EliminarPedido(pedidos, borrado);

  @Test
  void unPagoFallidoSinNadaColgandoSeBorraYDevuelveSuNumero() {
    Pedido pedido = pedidoEn(EstadoPedido.PAGO_FALLIDO);

    NumeroPedido numero = eliminar.ejecutar(pedido.id());

    assertEquals(pedido.numeroPedido(), numero);
    assertEquals(List.of(pedido.id()), borrado.eliminados);
  }

  @Test
  void unCanceladoSinNadaColgandoTambienSeBorra() {
    Pedido pedido = pedidoEn(EstadoPedido.CANCELADO);

    eliminar.ejecutar(pedido.id());

    assertEquals(List.of(pedido.id()), borrado.eliminados);
  }

  /** Esperando el pago, pagado o en camino: hay dinero o inventario en juego. Se cancela. */
  @Test
  void enCualquierOtroEstadoNoSeBorra() {
    for (EstadoPedido estado :
        List.of(EstadoPedido.PAGO_PENDIENTE, EstadoPedido.PAGADO, EstadoPedido.EN_PREPARACION)) {
      Pedido pedido = pedidoEn(estado);

      PedidoNoEliminableException error =
          assertThrows(PedidoNoEliminableException.class, () -> eliminar.ejecutar(pedido.id()));
      assertTrue(error.getMessage().contains(estado.name()), error.getMessage());
    }
    assertTrue(borrado.eliminados.isEmpty());
  }

  /**
   * Cancelado después de cobrar: tiene su pago y su reintegro, y la compra pagada se conserva por
   * la ley tributaria. El estado solo no basta.
   */
  @Test
  void unCanceladoConAlgoColgandoNoSeBorra() {
    for (BorradoDePedidos.Compromiso compromiso : BorradoDePedidos.Compromiso.values()) {
      Pedido pedido = pedidoEn(EstadoPedido.CANCELADO);
      borrado.compromisos.put(pedido.id(), EnumSet.of(compromiso));

      assertThrows(PedidoNoEliminableException.class, () -> eliminar.ejecutar(pedido.id()));
    }
    assertTrue(borrado.eliminados.isEmpty());
  }

  /**
   * El dinero de una transferencia no deja fila en `pago`: cancelada, puede tener una consignación
   * que nadie vio, y el sistema no tiene cómo saberlo.
   */
  @Test
  void unaTransferenciaManualCanceladaNoSeBorra() {
    Pedido pedido = pedidoEn(EstadoPedido.CANCELADO, MetodoPago.TRANSFERENCIA_MANUAL);

    assertThrows(PedidoNoEliminableException.class, () -> eliminar.ejecutar(pedido.id()));
    assertTrue(borrado.eliminados.isEmpty());
  }

  @Test
  void elQueNoExisteSeDice() {
    assertThrows(PedidoNoEncontradoException.class, () -> eliminar.ejecutar(UUID.randomUUID()));
  }

  private int secuencia;

  private Pedido pedidoEn(EstadoPedido estado) {
    return pedidoEn(estado, MetodoPago.WOMPI);
  }

  private Pedido pedidoEn(EstadoPedido estado, MetodoPago metodoPago) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, ++secuencia),
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
                    new BigDecimal("0.00"),
                    "https://cdn.tecnosport.co/img.webp",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            metodoPago,
            "cliente@tecnosport.co",
            AHORA.minusSeconds(500));
    switch (estado) {
      case PAGO_PENDIENTE -> {}
      case PAGO_FALLIDO ->
          pedido.transicionar(EstadoPedido.PAGO_FALLIDO, "webhook-wompi", "rechazado", AHORA);
      case CANCELADO ->
          pedido.transicionar(EstadoPedido.CANCELADO, "admin:1", "sin existencia", AHORA);
      case PAGADO -> pedido.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "aprobado", AHORA);
      case EN_PREPARACION -> {
        pedido.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "aprobado", AHORA);
        pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:1", "listo", AHORA);
      }
      default -> throw new IllegalArgumentException("Sin escenario para " + estado);
    }
    pedidos.guardar(pedido);
    return pedido;
  }

  private static final class BorradoFalso implements BorradoDePedidos {
    final Map<UUID, Set<Compromiso>> compromisos = new HashMap<>();
    final List<UUID> eliminados = new ArrayList<>();

    @Override
    public Set<Compromiso> compromisosDe(UUID pedidoId) {
      return compromisos.getOrDefault(pedidoId, Set.of());
    }

    @Override
    public void eliminar(UUID pedidoId) {
      eliminados.add(pedidoId);
    }
  }
}
