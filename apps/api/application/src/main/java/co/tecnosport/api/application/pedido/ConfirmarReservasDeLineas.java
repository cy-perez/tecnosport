package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.ReservaNoEncontradaException;
import co.tecnosport.api.domain.inventario.ReservaYaProcesadaException;
import co.tecnosport.api.domain.pedido.LineaPedido;
import java.time.Instant;
import java.util.List;

/**
 * Confirma la reserva de cada línea de un pedido (la convierte en salida real), compartido entre
 * {@code AplicadorDeResultadoDePago} (pago aprobado) y {@code ConciliarTransferencia}
 * (transferencia conciliada): ambos llegan al mismo punto — un pago resuelto sin nada más que
 * verificar no debería poder avanzar el pedido sin que el inventario detrás quede confirmado
 * también.
 */
public final class ConfirmarReservasDeLineas {

  private ConfirmarReservasDeLineas() {}

  /** {@code true} solo si todas las líneas confirmaron su reserva. */
  public static boolean confirmar(
      List<LineaPedido> lineas, Instant ahora, RepositorioInventario repositorioInventario) {
    boolean todoBien = true;
    for (LineaPedido linea : lineas) {
      Inventario inventario =
          repositorioInventario.buscarPorVarianteId(linea.varianteId()).orElse(null);
      if (inventario == null) {
        todoBien = false;
        continue;
      }
      try {
        inventario.confirmar(linea.idReserva(), ahora);
        repositorioInventario.guardar(inventario);
      } catch (ReservaYaProcesadaException | ReservaNoEncontradaException excepcion) {
        todoBien = false;
      }
    }
    return todoBien;
  }
}
