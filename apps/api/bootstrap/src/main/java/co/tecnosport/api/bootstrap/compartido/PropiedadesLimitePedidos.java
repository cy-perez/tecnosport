package co.tecnosport.api.bootstrap.compartido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Perfil de límite de intentos para creación de pedidos (docs/08-seguridad-legal.md), más generoso
 * que {@link PropiedadesLimiteAuth}: reintentar una compra es normal, automatizar intentos de login
 * no lo es.
 */
@ConfigurationProperties(prefix = "tecnosport.limite-intentos.pedidos")
public record PropiedadesLimitePedidos(
    int ipMaximo, int ipMinutos, int cuentaMaximo, int cuentaMinutos) {

  public PropiedadesLimitePedidos {
    if (ipMaximo <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.pedidos.ip-maximo debe ser mayor que cero.");
    }
    if (ipMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.pedidos.ip-minutos debe ser mayor que cero.");
    }
    if (cuentaMaximo <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.pedidos.cuenta-maximo debe ser mayor que cero.");
    }
    if (cuentaMinutos <= 0) {
      throw new IllegalStateException(
          "tecnosport.limite-intentos.pedidos.cuenta-minutos debe ser mayor que cero.");
    }
  }
}
