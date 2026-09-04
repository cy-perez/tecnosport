package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Regresa un pedido {@code PAGO_FALLIDO} a {@code PAGO_PENDIENTE} para que el cliente pueda pedir
 * un intento de pago nuevo con {@code CrearIntentoDePago} — ese caso de uso deliberadamente no
 * acepta pedidos en {@code PAGO_FALLIDO} directo, así que hacía falta este paso aparte.
 *
 * <p>docs/02-modelo-datos.md: "el pago rechazado... la libera" — la reserva original de cada línea
 * ya no existe cuando el pedido llega aquí ({@code AplicadorDeResultadoDePago} la liberó al marcar
 * {@code PAGO_FALLIDO}). Reintentar exige una reserva nueva, revalidada contra la existencia real:
 * si ya no alcanza (se vendió mientras tanto), el reintento falla con {@code
 * ExistenciaInsuficienteException} — un caso de negocio real, no un error.
 *
 * <p>Solo un pedido procesado por Wompi llega a {@code PAGO_FALLIDO}: transferencia manual y
 * contraentrega nunca pasan por {@code AplicadorDeResultadoDePago}. El estado ya lo garantiza, así
 * que no hace falta revalidar el método de pago aparte, y la duración de reserva de pago en línea
 * siempre aplica.
 */
public final class ReintentarPago {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;
  private final Duration duracionReservaPagoEnLinea;

  public ReintentarPago(
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario,
      Reloj reloj,
      Duration duracionReservaPagoEnLinea) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
    this.duracionReservaPagoEnLinea = Objects.requireNonNull(duracionReservaPagoEnLinea);
  }

  public Pedido ejecutar(ReintentarPagoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    // Se valida antes de reservar: si el pedido no estaba en PAGO_FALLIDO, reservar de todos modos
    // dejaría una reserva huérfana que nadie libera.
    if (!pedido.estado().puedeTransicionarA(EstadoPedido.PAGO_PENDIENTE)) {
      throw new TransicionDeEstadoInvalidaException(pedido.estado(), EstadoPedido.PAGO_PENDIENTE);
    }
    Instant ahora = reloj.ahora();
    Map<UUID, UUID> nuevasReservasPorLineaId = reReservar(pedido, ahora);
    pedido.actualizarReservas(nuevasReservasPorLineaId);
    pedido.transicionar(
        EstadoPedido.PAGO_PENDIENTE, pedido.correo().valor(), "reintento de pago", ahora);
    repositorioPedidos.guardar(pedido);
    return pedido;
  }

  private Map<UUID, UUID> reReservar(Pedido pedido, Instant ahora) {
    Map<UUID, UUID> nuevasReservasPorLineaId = new LinkedHashMap<>();
    for (LineaPedido linea : pedido.lineas()) {
      Inventario inventario =
          repositorioInventario
              .buscarPorVarianteId(linea.varianteId())
              .orElseThrow(() -> new VarianteNoEncontradaException(linea.varianteId()));
      MovimientoInventario reserva =
          inventario.reservar(linea.cantidad(), duracionReservaPagoEnLinea, ahora);
      repositorioInventario.guardar(inventario);
      nuevasReservasPorLineaId.put(linea.id(), reserva.id());
    }
    return nuevasReservasPorLineaId;
  }
}
