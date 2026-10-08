package co.tecnosport.api.application.usuario;

/**
 * Comprueba una credencial de "Iniciar sesión con Google" (un ID token): la firma de Google, que es
 * para esta aplicación y que no ha vencido. Lo implementa infraestructura; aquí solo se decide qué
 * hacer con la identidad (ADR-0074).
 */
public interface VerificadorDeCredencialGoogle {

  /**
   * @throws CredencialGoogleInvalidaException si la credencial no la firmó Google, es para otra
   *     aplicación o ya venció
   */
  IdentidadGoogle verificar(String credencial);
}
