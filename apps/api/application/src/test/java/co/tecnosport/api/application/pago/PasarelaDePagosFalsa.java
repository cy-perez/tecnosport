package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.List;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. Firma determinista, sin red;
 * {@code verificarFirmaEvento} responde válida salvo que se pida lo contrario con {@link
 * #conFirmaEventoInvalida()}.
 */
final class PasarelaDePagosFalsa implements PasarelaDePagos {

  private boolean firmaEventoValida = true;

  void conFirmaEventoInvalida() {
    this.firmaEventoValida = false;
  }

  @Override
  public String generarFirmaIntegridad(ReferenciaPago referencia, Dinero monto) {
    return "firma-falsa:" + referencia.valor() + ":" + monto.valor();
  }

  @Override
  public boolean verificarFirmaEvento(
      List<String> valoresPropiedades, long timestamp, String checksum) {
    return firmaEventoValida;
  }
}
