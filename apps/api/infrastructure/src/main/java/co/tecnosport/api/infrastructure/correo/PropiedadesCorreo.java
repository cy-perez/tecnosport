package co.tecnosport.api.infrastructure.correo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dirección "De:" de los correos transaccionales (docs/07-infra-gcp.md: {@code CORREO_REMITENTE}).
 * Dato de negocio real, no inventado aquí — el valor por defecto en {@code application.yml} es el
 * dominio real del proyecto, no un placeholder.
 */
@ConfigurationProperties(prefix = "tecnosport.correo")
public record PropiedadesCorreo(String remitente) {

  public PropiedadesCorreo {
    if (remitente == null || remitente.isBlank()) {
      throw new IllegalStateException("tecnosport.correo.remitente no puede estar vacío.");
    }
  }
}
