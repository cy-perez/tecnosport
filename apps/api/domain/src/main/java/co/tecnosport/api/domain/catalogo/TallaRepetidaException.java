package co.tecnosport.api.domain.catalogo;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/** La talla corregida ya existe en alguno de los colores del modelo: quedarían dos iguales. */
public final class TallaRepetidaException extends ExcepcionDeDominio {

  public TallaRepetidaException(String mensaje) {
    super(mensaje);
  }
}
