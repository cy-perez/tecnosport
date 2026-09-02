package co.tecnosport.api.domain.inventario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;

/** La reserva ya se confirmó o se liberó antes: no se puede resolver dos veces. */
public final class ReservaYaProcesadaException extends ExcepcionDeDominio {

  public ReservaYaProcesadaException(UUID idReserva) {
    super("La reserva " + idReserva + " ya fue confirmada o liberada.");
  }
}
