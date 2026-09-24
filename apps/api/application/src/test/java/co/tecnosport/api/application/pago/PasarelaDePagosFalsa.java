package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. Firma determinista, sin red;
 * {@code consultarTransaccion} responde lo que se configure, o vacío si no se configuró nada para
 * ese id.
 *
 * <p><strong>Este doble sabe mentir, y esa es la razón de que se reescribiera.</strong> Antes solo
 * llevaba estado y medio, así que no existía el concepto de "una transacción que no es la de este
 * pago" y la conciliación no podía probarse contra él: un doble que no puede mentir no puede
 * demostrar que a alguien se le cree. {@link #conTransaccionDe} arma la que sí corresponde y {@link
 * #conTransaccionAjena} la que no.
 */
final class PasarelaDePagosFalsa implements PasarelaDePagos {

  private boolean firmaEventoValida = true;
  private final Map<String, TransaccionDePasarela> transacciones = new HashMap<>();

  void conFirmaEventoInvalida() {
    this.firmaEventoValida = false;
  }

  /** Sin medio: Wompi no siempre lo trae, y el estado tiene que bastar para conciliar. */
  void conTransaccionDe(String idTransaccionPasarela, String estado, Pago pago) {
    conTransaccionDe(idTransaccionPasarela, estado, null, pago);
  }

  /** La transacción que de verdad corresponde a ese pago: su referencia y su monto. */
  void conTransaccionDe(String idTransaccionPasarela, String estado, String medio, Pago pago) {
    transacciones.put(
        idTransaccionPasarela,
        new TransaccionDePasarela(estado, medio, pago.referencia().valor(), pago.monto()));
  }

  /**
   * Una transacción real de la pasarela que no es la de este pago: otra referencia, otro monto, o
   * las dos cosas. Es lo que ve la conciliación cuando alguien estampa sobre su pedido el id de una
   * transacción ajena.
   */
  void conTransaccionAjena(
      String idTransaccionPasarela, String estado, String referencia, Dinero monto) {
    transacciones.put(
        idTransaccionPasarela, new TransaccionDePasarela(estado, null, referencia, monto));
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
  public Optional<TransaccionDePasarela> consultarTransaccion(String idTransaccionPasarela) {
    return Optional.ofNullable(transacciones.get(idTransaccionPasarela));
  }
}
