package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.Pago;
import java.util.Objects;
import java.util.UUID;

/**
 * Quien administra buscó el pago en el portal de Wompi y ya no hace falta que la alerta lo enseñe.
 * No toca el pago ni el pedido: si en el portal resultó cobrado, lo que corresponde es registrar
 * ese id de transacción para que la conciliación lo cierre, no esta marca.
 */
public final class MarcarPagoSinTransaccionRevisado {

  private final RepositorioPagos repositorioPagos;
  private final Reloj reloj;

  public MarcarPagoSinTransaccionRevisado(RepositorioPagos repositorioPagos, Reloj reloj) {
    this.repositorioPagos = Objects.requireNonNull(repositorioPagos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public void ejecutar(UUID pagoId, String actor) {
    Objects.requireNonNull(pagoId, "El id del pago no puede ser nulo.");
    Objects.requireNonNull(actor, "El actor no puede ser nulo.");
    Pago pago =
        repositorioPagos
            .buscarPorId(pagoId)
            .orElseThrow(() -> new PagoNoEncontradoException(pagoId.toString()));
    if (pago.estado() != EstadoPago.PENDIENTE || pago.idTransaccionPasarela().isPresent()) {
      throw new PagoConTransaccionOResueltoException();
    }
    repositorioPagos.marcarRevisadoSinTransaccion(pagoId, actor, reloj.ahora());
  }
}
