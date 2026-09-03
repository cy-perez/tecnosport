package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.pago.PasarelaDePagos;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;

final class PasarelaDePagosDobleDePrueba implements PasarelaDePagos {

  @Override
  public String generarFirmaIntegridad(ReferenciaPago referencia, Dinero monto) {
    return "firma-de-prueba:" + referencia.valor();
  }
}
