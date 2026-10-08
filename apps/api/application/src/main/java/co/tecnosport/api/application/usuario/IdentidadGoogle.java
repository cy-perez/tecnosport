package co.tecnosport.api.application.usuario;

import java.util.Objects;

/**
 * Lo que una credencial de Google ya verificada dice de quien entra (ADR-0074). {@code sub} es el
 * identificador estable de la cuenta de Google; el correo puede cambiar, el {@code sub} no.
 */
public record IdentidadGoogle(String sub, String correo, boolean correoVerificado) {

  public IdentidadGoogle {
    Objects.requireNonNull(sub, "El identificador de Google no puede ser nulo.");
    Objects.requireNonNull(correo, "El correo de Google no puede ser nulo.");
  }
}
