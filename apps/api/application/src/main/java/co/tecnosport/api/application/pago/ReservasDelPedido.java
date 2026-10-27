package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lo que los dos casos de uso de intento de pago comprueban antes de crear el {@code Pago}: que
 * cada línea sigue con su unidad apartada.
 *
 * <p>Sin esto se cobraba un pedido en {@code PAGO_PENDIENTE} cuya reserva había vencido hacía
 * horas: la unidad se vendía a otro comprador, el pago se aprobaba igual y el pedido quedaba {@code
 * PAGADO} con {@code APLICADO_SIN_CONFIRMAR_INVENTARIO} — dinero recibido por una unidad que no
 * existe. {@code Inventario.confirmarTarde} salva el caso en que la unidad sigue ahí, pero ya
 * después de cobrar; aquí se corta antes.
 *
 * <p>La lectura va <b>sin bloqueo</b> ({@link RepositorioInventario#buscarPorVarianteIds}): no se
 * escribe nada en el inventario, y bloquear las variantes de un pedido para leerlas obligaría a
 * hacerlo en orden para no interbloquearse con {@code CrearPedido}. No cierra la ventana de una
 * reserva que vence mientras el comprador paga —eso lo atiende {@code confirmarTarde}—; cierra la
 * de un intento que empieza con la reserva ya perdida.
 */
final class ReservasDelPedido {

  private ReservasDelPedido() {}

  static void exigirVigentes(
      Pedido pedido, RepositorioInventario repositorioInventario, Instant ahora) {
    Map<UUID, Inventario> porVariante =
        repositorioInventario
            .buscarPorVarianteIds(
                pedido.lineas().stream().map(LineaPedido::varianteId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Inventario::varianteId, Function.identity()));
    for (LineaPedido linea : pedido.lineas()) {
      Inventario inventario = porVariante.get(linea.varianteId());
      if (inventario == null || !inventario.reservaSigueVigente(linea.idReserva(), ahora)) {
        throw new ReservaDelPedidoVencidaException();
      }
    }
  }
}
