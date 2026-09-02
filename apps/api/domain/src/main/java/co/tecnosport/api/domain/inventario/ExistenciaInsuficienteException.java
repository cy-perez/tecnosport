package co.tecnosport.api.domain.inventario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

public final class ExistenciaInsuficienteException extends ExcepcionDeDominio {

  public ExistenciaInsuficienteException(String mensaje) {
    super(mensaje);
  }
}
