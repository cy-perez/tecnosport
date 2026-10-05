package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.pago.PasarelaDePagos;
import co.tecnosport.api.application.pago.TransaccionDePasarela;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.List;
import java.util.Optional;

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

  private final java.util.Map<String, TransaccionDePasarela> transacciones =
      new java.util.HashMap<>();

  /** Una transacción que la pasarela reporta con esa referencia y ese monto. */
  void conTransaccion(String id, String referencia, Dinero monto) {
    transacciones.put(id, new TransaccionDePasarela("PENDING", null, referencia, monto));
  }

  void limpiar() {
    transacciones.clear();
  }

  @Override
  public Optional<TransaccionDePasarela> consultarTransaccion(String idTransaccionPasarela) {
    return Optional.ofNullable(transacciones.get(idTransaccionPasarela));
  }
}
