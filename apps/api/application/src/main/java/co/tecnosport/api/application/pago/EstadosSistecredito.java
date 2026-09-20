package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.EstadoPago;

/**
 * Traduce el {@code transactionStatus} de Sistecrédito al {@link EstadoPago} del dominio, igual que
 * {@link EstadosWompi} hace con el {@code status} de Wompi. Valores tomados de la guía {@code
 * G-SCL-21} §3.3 y de la lista de estados no exitosos de {@code G-ALI-08}.
 *
 * <p>Los tres estados en vuelo —{@code Started}, {@code PendingForPaymentMethod}, {@code Pending}—
 * devuelven {@code null}: no son un resultado, son el camino. Aplicarlos sacaría al {@link
 * EstadoPago} de {@code PENDIENTE} antes de tiempo y la máquina de estados del dominio ya no
 * dejaría entrar el resultado de verdad.
 *
 * <p><b>{@code Cancelled}, {@code Expired} y {@code Abandoned} caen en {@code RECHAZADO} y no en
 * {@code ERROR}</b>, aunque suenen a accidente: lo que decide es qué tiene que pasarle al
 * inventario, y en los tres casos la compra no ocurrió y la reserva tiene que liberarse de
 * inmediato. {@code ERROR} se reserva para {@code Failed}, que es lo que la pasarela devuelve
 * cuando algo se rompió de su lado.
 */
final class EstadosSistecredito {

  private EstadosSistecredito() {}

  static EstadoPago aEstadoPago(String estado) {
    return switch (estado == null ? "" : estado.trim()) {
      case "Approved" -> EstadoPago.APROBADO;
      case "Rejected", "Cancelled", "Expired", "Abandoned" -> EstadoPago.RECHAZADO;
      case "Failed" -> EstadoPago.ERROR;
      default -> null;
    };
  }
}
