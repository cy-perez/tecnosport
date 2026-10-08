package co.tecnosport.api.application.usuario;

import java.util.Objects;

/**
 * Lo que una credencial de Google ya verificada dice de quien entra (ADR-0074). {@code sub} es el
 * identificador estable de la cuenta de Google; el correo puede cambiar, el {@code sub} no.
 *
 * <p>{@code googleEsAutoridad}: si Google manda sobre ese buzón — un {@code @gmail.com}, o un
 * dominio administrado por Google (el claim {@code hd}). Una cuenta personal de Google creada con
 * un correo de otro proveedor conserva {@code email_verified} aunque el buzón ya no sea de quien la
 * creó (un exempleado, un dominio que venció y alguien compró). Según la guía de Google para
 * verificar el ID token, en ese caso "Google is not authoritative".
 */
public record IdentidadGoogle(
    String sub, String correo, boolean correoVerificado, boolean googleEsAutoridad) {

  /** Para quien no distingue: un {@code @gmail.com} es autoridad, cualquier otro no. */
  public IdentidadGoogle(String sub, String correo, boolean correoVerificado) {
    this(
        sub,
        correo,
        correoVerificado,
        correo != null && correo.toLowerCase(java.util.Locale.ROOT).endsWith("@gmail.com"));
  }

  public IdentidadGoogle {
    Objects.requireNonNull(sub, "El identificador de Google no puede ser nulo.");
    Objects.requireNonNull(correo, "El correo de Google no puede ser nulo.");
  }
}
