package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.Pedido;

/**
 * Aplica un {@link EventoPago} a su {@link Pago} y, si corresponde, propaga el resultado al {@code
 * Pedido} — compartido entre {@code ProcesarEventoDePago} (webhook) y {@code
 * ConciliarPagosPendientes} (consulta directa), que llegan al mismo punto por caminos distintos.
 */
final class AplicadorDeResultadoDePago {

  private AplicadorDeResultadoDePago() {}

  static ResultadoEventoDePago aplicar(
      Pago pago,
      EventoPago evento,
      String actor,
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos) {
    boolean aplicado = pago.aplicarEvento(evento);
    if (!aplicado) {
      return ResultadoEventoDePago.YA_PROCESADO;
    }
    repositorioPagos.guardar(pago);
    propagarAlPedido(pago, evento, actor, repositorioPedidos);
    return ResultadoEventoDePago.APLICADO;
  }

  private static void propagarAlPedido(
      Pago pago, EventoPago evento, String actor, RepositorioPedidos repositorioPedidos) {
    EstadoPedido siguienteEstadoPedido =
        switch (evento.estado()) {
          case APROBADO -> EstadoPedido.PAGADO;
          case RECHAZADO, ERROR -> EstadoPedido.PAGO_FALLIDO;
          case PENDIENTE -> null;
        };
    if (siguienteEstadoPedido == null) {
      return;
    }
    Pedido pedido = repositorioPedidos.buscarPorId(pago.pedidoId()).orElse(null);
    // Un pedido ya resuelto por otro intento de pago no se toca: EstadoPedido ya rechazaría la
    // transición, pero comprobarlo antes evita depender de esa excepción como control de flujo.
    if (pedido != null && pedido.estado() == EstadoPedido.PAGO_PENDIENTE) {
      pedido.transicionar(
          siguienteEstadoPedido, actor, "evento de pago: " + evento.estado(), evento.recibidoEn());
      repositorioPedidos.guardar(pedido);
    }
  }
}
