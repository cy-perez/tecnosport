package co.tecnosport.api.bootstrap.compartido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Perfil de límite de intentos compartido por inicio de sesión, registro y recuperación
 * (docs/08-seguridad-legal.md): mismo tipo de abuso, automatizar intentos. Ningún documento da
 * números — son un dato de seguridad, no de negocio, ajustable por variable de entorno.
 */
@ConfigurationProperties(prefix = "tecnosport.limite-intentos.auth")
public record PropiedadesLimiteAuth(
    int ipMaximo, int ipMinutos, int cuentaMaximo, int cuentaMinutos) {

  public PropiedadesLimiteAuth {
    if (ipMaximo <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.auth.ip-maximo debe ser mayor que cero.");
    }
    if (ipMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.auth.ip-minutos debe ser mayor que cero.");
    }
    if (cuentaMaximo <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.auth.cuenta-maximo debe ser mayor que cero.");
    }
    if (cuentaMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.auth.cuenta-minutos debe ser mayor que cero.");
    }
  }
}
