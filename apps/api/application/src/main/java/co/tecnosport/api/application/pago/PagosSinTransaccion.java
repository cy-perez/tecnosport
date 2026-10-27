package co.tecnosport.api.application.pago;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * La alerta entera: los pagos y el umbral con que se eligieron, para que el panel diga "más de 12
 * horas" con el número que de verdad se aplicó y no con uno escrito en un texto.
 */
public record PagosSinTransaccion(Duration umbral, List<PagoSinTransaccion> pagos) {

  public PagosSinTransaccion {
    Objects.requireNonNull(umbral, "El umbral no puede ser nulo.");
    pagos = List.copyOf(Objects.requireNonNull(pagos, "Los pagos no pueden ser nulos."));
  }
}
