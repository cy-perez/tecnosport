package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.Objects;

/**
 * Lo que el frontend necesita para mandar al comprador a pagar con Sistecrédito.
 *
 * <p>A diferencia de {@link IntentoDePago}, aquí la URL llega hecha: la arma la pasarela, no
 * nosotros. Por eso no viaja ninguna llave pública ni ninguna firma — no hay nada que el navegador
 * tenga que componer, y por tanto nada que pueda alterar por el camino.
 *
 * <p>Es de un solo uso y la transacción vive unos 15 minutos (guía {@code G-ALI-12}).
 */
public record IntentoDePagoSistecredito(
    ReferenciaPago referencia, Dinero monto, String urlRedireccion) {

  public IntentoDePagoSistecredito {
    Objects.requireNonNull(referencia, "La referencia no puede ser nula.");
    Objects.requireNonNull(monto, "El monto no puede ser nulo.");
    if (urlRedireccion == null || urlRedireccion.isBlank()) {
      throw new IllegalArgumentException("La URL de redirección no puede estar vacía.");
    }
  }
}
