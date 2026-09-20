package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.EstadoPago;
import java.util.Locale;

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

  /**
   * <b>Insensible a mayúsculas a propósito.</b> Las guías son de 2023, no hay ambiente de pruebas
   * donde comprobar la grafía exacta, y el resto del código ya compara estados con {@code
   * equalsIgnoreCase}. Si la pasarela respondiera alguna vez {@code APPROVED}, un {@code switch}
   * exacto devolvería {@code null}: el pago se quedaría en {@code ESTADO_NO_SOPORTADO} —que sale
   * como una línea informativa—, la conciliación devolvería lo mismo en cada corrida para siempre,
   * la reserva vencería, y el resultado sería <b>un crédito aprobado y desembolsado con un pedido
   * que nunca avanzó</b>. Una línea de código contra eso es barata.
   */
  static EstadoPago aEstadoPago(String estado) {
    return switch (estado == null ? "" : estado.trim().toLowerCase(Locale.ROOT)) {
      case "approved" -> EstadoPago.APROBADO;
      case "rejected", "cancelled", "expired", "abandoned" -> EstadoPago.RECHAZADO;
      case "failed" -> EstadoPago.ERROR;
      default -> null;
    };
  }
}
