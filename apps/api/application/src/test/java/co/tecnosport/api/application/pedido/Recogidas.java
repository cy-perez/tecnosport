package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.pedido.Contacto;
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

/**
 * Pedidos de recogida en el punto con su unidad reservada, para las pruebas del camino del punto
 * (ADR-0094). El que se paga en el punto deja la reserva abierta, como al confirmarlo; el pagado en
 * línea la deja confirmada, como al entrar el dinero.
 */
final class Recogidas {

  private final RepositorioPedidosFalso pedidos;
  private final RepositorioInventarioFalso inventarios;
  private int secuencial;

  Recogidas(RepositorioPedidosFalso pedidos, RepositorioInventarioFalso inventarios) {
    this.pedidos = pedidos;
    this.inventarios = inventarios;
  }

  /** Confirmada sin pago y verificada: en preparación desde {@code creado}. */
  Pedido enPreparacion(MetodoPago metodoPago, Instant creado) {
    Pedido pedido = confirmada(metodoPago, creado);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:1", "verificado", creado);
    pedidos.guardar(pedido);
    return pedido;
  }

  /** Lista para recoger desde {@code listo}. */
  Pedido lista(MetodoPago metodoPago, Instant creado, Instant listo) {
    Pedido pedido = enPreparacion(metodoPago, creado);
    pedido.transicionar(EstadoPedido.LISTO_PARA_RECOGER, "admin:1", "listo", listo);
    pedidos.guardar(pedido);
    return pedido;
  }

  /** Recién confirmada: {@code CONFIRMADO_CONTRAENTREGA} si se paga en el punto, o pagada. */
  Pedido confirmada(MetodoPago metodoPago, Instant creado) {
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(1, "siembra", creado.minus(Duration.ofDays(10)));
    boolean pagaAlRecoger = metodoPago == MetodoPago.PAGO_EN_PUNTO;
    MovimientoInventario reserva =
        inventario.reservar(1, pagaAlRecoger ? null : Duration.ofMinutes(30), creado);
    if (!pagaAlRecoger) {
      inventario.confirmar(reserva.id(), creado);
    }
    inventarios.conInventario(inventario);
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, ++secuencial),
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
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            metodoPago,
            "cliente@tecnosport.co",
            creado,
            null,
            new Contacto("Ana Pérez", "3138816711"));
    if (!pagaAlRecoger) {
      pedido.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "pago aprobado", creado);
    }
    pedidos.guardar(pedido);
    return pedido;
  }

  Inventario inventarioDe(Pedido pedido) {
    return inventarios.buscarPorVarianteId(pedido.lineas().get(0).varianteId()).orElseThrow();
  }
}
