package co.tecnosport.api.domain.inventario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;

public final class ReservaNoEncontradaException extends ExcepcionDeDominio {

  public ReservaNoEncontradaException(UUID idReserva) {
    super("No existe una reserva con id " + idReserva + ".");
  }
}
