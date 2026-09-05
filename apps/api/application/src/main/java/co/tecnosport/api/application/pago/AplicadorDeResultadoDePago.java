package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.ConfirmarReservasDeLineas;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.ReservaNoEncontradaException;
import co.tecnosport.api.domain.inventario.ReservaYaProcesadaException;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.Pedido;

/**
 * Aplica un {@link EventoPago} a su {@link Pago} y, si corresponde, propaga el resultado al {@code
 * Pedido} y a su inventario reservado — compartido entre {@code ProcesarEventoDePago} (webhook) y
 * {@code ConciliarPagosPendientes} (consulta directa), que llegan al mismo punto por caminos
 * distintos.
 *
 * <p>docs/02-modelo-datos.md: "el pago aprobado convierte reserva en salida; el pago rechazado o
 * vencido la libera". Un evento aprobado sobre una reserva que ya venció o se resolvió antes (un
 * webhook tardío, o la conciliación llegando después de los 30 minutos de la reserva) no se puede
 * confirmar a ciegas: la unidad pudo haberse vendido a otro comprador ya. En ese caso el pago y el
 * pedido igual se actualizan —la plata ya entró, eso no se revierte— pero se marca aparte para
 * revisión manual en vez de arriesgar una sobreventa silenciosa.
 */
final class AplicadorDeResultadoDePago {

  private AplicadorDeResultadoDePago() {}

  static ResultadoEventoDePago aplicar(
      Pago pago,
      EventoPago evento,
      String actor,
      RepositorioPagos repositorioPagos,
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario) {
    boolean aplicado = pago.aplicarEvento(evento);
    if (!aplicado) {
      return ResultadoEventoDePago.YA_PROCESADO;
    }
    repositorioPagos.guardar(pago);
    return propagarAlPedido(pago, evento, actor, repositorioPedidos, repositorioInventario);
  }

  private static ResultadoEventoDePago propagarAlPedido(
      Pago pago,
      EventoPago evento,
      String actor,
      RepositorioPedidos repositorioPedidos,
      RepositorioInventario repositorioInventario) {
    EstadoPedido siguienteEstadoPedido =
        switch (evento.estado()) {
          case APROBADO -> EstadoPedido.PAGADO;
          case RECHAZADO, ERROR -> EstadoPedido.PAGO_FALLIDO;
          case PENDIENTE -> null;
        };
    if (siguienteEstadoPedido == null) {
      return ResultadoEventoDePago.APLICADO;
    }
    Pedido pedido = repositorioPedidos.buscarPorId(pago.pedidoId()).orElse(null);
    // Un pedido ya resuelto por otro intento de pago no se toca: EstadoPedido ya rechazaría la
    // transición, pero comprobarlo antes evita depender de esa excepción como control de flujo.
    if (pedido == null || pedido.estado() != EstadoPedido.PAGO_PENDIENTE) {
      return ResultadoEventoDePago.APLICADO;
    }
    pedido.transicionar(
        siguienteEstadoPedido, actor, "evento de pago: " + evento.estado(), evento.recibidoEn());
    boolean inventarioOk =
        actualizarInventario(pedido, siguienteEstadoPedido, evento, repositorioInventario);
    // Un pago aprobado con el inventario confirmado queda listo para preparar de una vez, sin un
    // clic extra en el panel (docs/02-modelo-datos.md: PAGADO -> EN_PREPARACION es el mismo camino
    // que ya recorre CONFIRMADO_CONTRAENTREGA vía VerificarContraentrega). Si el inventario no se
    // pudo confirmar (reserva vencida, revisión manual), el pedido se queda en PAGADO a propósito:
    // no tiene sentido decirle al almacén que prepare algo con un inventario en duda.
    if (siguienteEstadoPedido == EstadoPedido.PAGADO && inventarioOk) {
      pedido.transicionar(
          EstadoPedido.EN_PREPARACION,
          actor,
          "pago aprobado, listo para preparar",
          evento.recibidoEn());
    }
    repositorioPedidos.guardar(pedido);
    return inventarioOk
        ? ResultadoEventoDePago.APLICADO
        : ResultadoEventoDePago.APLICADO_SIN_CONFIRMAR_INVENTARIO;
  }

  private static boolean actualizarInventario(
      Pedido pedido,
      EstadoPedido siguienteEstadoPedido,
      EventoPago evento,
      RepositorioInventario repositorioInventario) {
    if (siguienteEstadoPedido == EstadoPedido.PAGADO) {
      return ConfirmarReservasDeLineas.confirmar(
          pedido.lineas(), evento.recibidoEn(), repositorioInventario);
    }
    boolean todoBien = true;
    for (LineaPedido linea : pedido.lineas()) {
      Inventario inventario =
          repositorioInventario.buscarPorVarianteId(linea.varianteId()).orElse(null);
      if (inventario == null) {
        todoBien = false;
        continue;
      }
      try {
        inventario.liberar(linea.idReserva(), "pago " + evento.estado(), evento.recibidoEn());
        repositorioInventario.guardar(inventario);
      } catch (ReservaYaProcesadaException | ReservaNoEncontradaException excepcion) {
        todoBien = false;
      }
    }
    return todoBien;
  }
}
