package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. Firma determinista, sin red.
 */
final class PasarelaDePagosFalsa implements PasarelaDePagos {

  @Override
  public String generarFirmaIntegridad(ReferenciaPago referencia, Dinero monto) {
    return "firma-falsa:" + referencia.valor() + ":" + monto.valor();
  }
}
