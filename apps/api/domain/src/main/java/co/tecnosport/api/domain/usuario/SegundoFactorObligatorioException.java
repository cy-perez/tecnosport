package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/** El panel exige segundo factor (ADMIN_EXIGE_SEGUNDO_FACTOR), así que no se puede desactivar. */
public final class SegundoFactorObligatorioException extends ExcepcionDeDominio {

  public SegundoFactorObligatorioException() {
    super("El segundo factor es obligatorio para esta cuenta y no se puede desactivar.");
  }
}
