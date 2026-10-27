package co.tecnosport.api.bootstrap.compartido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Perfil de límite de intentos compartido por inicio de sesión, registro y recuperación
 * (docs/08-seguridad-legal.md): mismo tipo de abuso, automatizar intentos. Ningún documento da
 * números — son un dato de seguridad, no de negocio, ajustable por variable de entorno.
 *
 * <p>En el inicio de sesión {@code cuentaMaximo} se cuenta por cuenta <i>e IP</i>, y {@code
 * cuentaGlobalMaximo} por cuenta sola: con el correo del administrador público, un tope bajo por
 * cuenta sola le dejaba a cualquiera cerrar el panel (ver {@code IniciarSesion}).
 */
@ConfigurationProperties(prefix = "tecnosport.limite-intentos.auth")
public record PropiedadesLimiteAuth(
    int ipMaximo, int ipMinutos, int cuentaMaximo, int cuentaMinutos, int cuentaGlobalMaximo) {

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
    if (cuentaGlobalMaximo < cuentaMaximo) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.auth.cuenta-global-maximo no puede ser menor que"
              + " cuenta-maximo: cerraría la cuenta antes que al origen que falla.");
    }
    if (cuentaMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.auth.cuenta-minutos debe ser mayor que cero.");
    }
  }
}
