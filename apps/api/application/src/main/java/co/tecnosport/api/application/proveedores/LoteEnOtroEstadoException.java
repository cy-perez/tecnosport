package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.EstadoLote;

/**
 * La orden del panel ya no aplica: el lote cambió de estado entre que se pintó el botón y se pulsó
 * —terminó, falló, o lo detuvo otra pestaña—. Es un {@code 409} con su propio código para que el
 * panel diga eso, y no "vuelve a intentarlo", que es justo lo que no hay que hacer.
 */
public final class LoteEnOtroEstadoException extends RuntimeException {

  public LoteEnOtroEstadoException(EstadoLote estado) {
    super("La ingesta cambió de estado mientras tanto: ahora está " + estado + ".");
  }
}
