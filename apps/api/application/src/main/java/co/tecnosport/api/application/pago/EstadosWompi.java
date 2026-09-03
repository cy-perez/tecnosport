package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.EstadoPago;

/**
 * Traduce el {@code status} que trae Wompi (por webhook o por consulta directa a la API) al {@link
 * EstadoPago} del dominio. Compartido entre {@code ProcesarEventoDePago} y {@code
 * ConciliarPagosPendientes} para no repetir el mapeo — llegan al mismo estado por caminos
 * distintos.
 *
 * <p>{@code VOIDED} (una transacción aprobada que luego se anula) y cualquier otro valor no
 * contemplado devuelven {@code null} a propósito: el grafo de {@link EstadoPago} de este alcance
 * solo sale de {@code PENDIENTE}. Anulaciones y reembolsos son un caso de negocio aparte.
 */
final class EstadosWompi {

  private EstadosWompi() {}

  static EstadoPago aEstadoPago(String estadoWompi) {
    return switch (estadoWompi) {
      case "APPROVED" -> EstadoPago.APROBADO;
      case "DECLINED" -> EstadoPago.RECHAZADO;
      case "ERROR" -> EstadoPago.ERROR;
      default -> null;
    };
  }
}
