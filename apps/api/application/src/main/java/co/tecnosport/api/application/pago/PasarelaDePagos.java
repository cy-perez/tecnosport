package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;

/**
 * Puerto hacia Wompi (docs/01-arquitectura.md). Implementación de producción: cliente HTTP de
 * Wompi, en infrastructure. Implementación de prueba: una firma determinista, sin red.
 */
public interface PasarelaDePagos {

  /**
   * Firma de integridad exigida por Wompi al crear una transacción (docs/11-pagos-y-envios.md),
   * calculada sobre la referencia, el monto y la moneda — así nadie altera el precio en el camino
   * hacia el checkout hospedado. Consultar una transacción y verificar la firma de un webhook se
   * agregan a este puerto cuando se construyan esos casos de uso.
   */
  String generarFirmaIntegridad(ReferenciaPago referencia, Dinero monto);
}
