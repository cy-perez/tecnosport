package co.tecnosport.api.application.usuario;

/**
 * Hay una cuenta con ese correo, y Google no manda sobre ese buzón (no es {@code @gmail.com} ni un
 * dominio administrado por Google): unirla por el correo sería entregársela a quien tenga una
 * cuenta de Google con ese correo, sea o no el dueño del buzón hoy. Se le pide entrar con su
 * contraseña (ADR-0074).
 */
public final class CuentaExistenteRequiereClaveException extends RuntimeException {

  public CuentaExistenteRequiereClaveException() {
    super("Ya hay una cuenta con ese correo: entra con tu contraseña.");
  }
}
