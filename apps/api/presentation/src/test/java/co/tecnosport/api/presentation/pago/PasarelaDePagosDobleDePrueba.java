package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.pago.PasarelaDePagos;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.List;

final class PasarelaDePagosDobleDePrueba implements PasarelaDePagos {

  @Override
  public String generarFirmaIntegridad(ReferenciaPago referencia, Dinero monto) {
    return "firma-de-prueba:" + referencia.valor();
  }

  @Override
  public boolean verificarFirmaEvento(
      List<String> valoresPropiedades, long timestamp, String checksum) {
    return "checksum-valido".equals(checksum);
  }
}
