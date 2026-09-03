package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.Objects;

/**
 * Lo que el frontend necesita para construir la URL del Web Checkout de Wompi
 * (docs/11-pagos-y-envios.md). La llave pública y la URL base de Wompi no van aquí: son
 * configuración de ambiente, no un dato de este intento — las resuelve presentation.
 */
public record IntentoDePago(ReferenciaPago referencia, Dinero monto, String firmaIntegridad) {

  public IntentoDePago {
    Objects.requireNonNull(referencia, "La referencia no puede ser nula.");
    Objects.requireNonNull(monto, "El monto no puede ser nulo.");
    Objects.requireNonNull(firmaIntegridad, "La firma de integridad no puede ser nula.");
  }
}
