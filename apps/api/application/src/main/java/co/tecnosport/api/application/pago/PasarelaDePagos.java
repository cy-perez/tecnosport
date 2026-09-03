package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.List;

/**
 * Puerto hacia Wompi (docs/01-arquitectura.md). Implementación de producción: cliente HTTP de
 * Wompi, en infrastructure. Implementación de prueba: una firma determinista, sin red.
 */
public interface PasarelaDePagos {

  /**
   * Firma de integridad exigida por Wompi al crear una transacción (docs/11-pagos-y-envios.md),
   * calculada sobre la referencia, el monto y la moneda — así nadie altera el precio en el camino
   * hacia el checkout hospedado. Consultar una transacción se agrega a este puerto cuando se
   * construya la conciliación programada.
   */
  String generarFirmaIntegridad(ReferenciaPago referencia, Dinero monto);

  /**
   * Verifica el checksum de un evento de webhook (docs/11-pagos-y-envios.md: "la firma del webhook
   * se verifica siempre"). {@code valoresPropiedades} son los valores, no los nombres, de las
   * propiedades que Wompi declaró como firmadas para este evento — el algoritmo de Wompi es {@code
   * SHA256(concat(valoresPropiedades) + timestamp + secretoEventos)}.
   */
  boolean verificarFirmaEvento(List<String> valoresPropiedades, long timestamp, String checksum);
}
