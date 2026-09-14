package co.tecnosport.api.bootstrap.usuario;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RECUPERACION_CLAVE_MINUTOS_VENCIMIENTO de docs/07-infra-gcp.md. La URL pública para el enlace del
 * correo sale de {@code co.tecnosport.api.bootstrap.compartido.PropiedadesApp} — no se duplica.
 */
@ConfigurationProperties(prefix = "tecnosport.recuperacion-clave")
public record PropiedadesRecuperacionClave(int minutosVencimiento) {

  public PropiedadesRecuperacionClave {
    if (minutosVencimiento <= 0) {
      throw new IllegalStateException(
          "tecnosport.recuperacion-clave.minutos-vencimiento debe ser mayor que cero.");
    }
  }
}
