package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. Firma determinista, sin red;
 * {@code verificarFirmaEvento} responde válida salvo que se pida lo contrario con {@link
 * #conFirmaEventoInvalida()}; {@code consultarTransaccion} responde lo que se configure con {@link
 * #conEstadoDeTransaccion(String, String)}, o vacío si no se configuró nada para ese id.
 */
final class PasarelaDePagosFalsa implements PasarelaDePagos {

  private boolean firmaEventoValida = true;
  private final Map<String, String> estadosPorTransaccion = new HashMap<>();

  void conFirmaEventoInvalida() {
    this.firmaEventoValida = false;
  }

  void conEstadoDeTransaccion(String idTransaccionWompi, String estado) {
    estadosPorTransaccion.put(idTransaccionWompi, estado);
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

  @Override
  public Optional<String> consultarTransaccion(String idTransaccionWompi) {
    return Optional.ofNullable(estadosPorTransaccion.get(idTransaccionWompi));
  }
}
