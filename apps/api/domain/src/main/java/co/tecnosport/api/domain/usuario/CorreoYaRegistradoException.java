package co.tecnosport.api.domain.usuario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;

/** Ya existe una cuenta con ese correo — el registro público no reutiliza cuentas existentes. */
public final class CorreoYaRegistradoException extends ExcepcionDeDominio {

  public CorreoYaRegistradoException() {
    super("Ya existe una cuenta registrada con ese correo.");
  }
}
