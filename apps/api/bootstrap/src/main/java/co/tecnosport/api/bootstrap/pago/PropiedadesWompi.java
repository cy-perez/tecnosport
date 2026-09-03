package co.tecnosport.api.bootstrap.pago;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code WOMPI_SECRETO_INTEGRIDAD} de docs/07-infra-gcp.md. Solo el secreto: la llave pública y el
 * ambiente no son secretos y los consume presentation directamente por su cuenta ({@code
 * PropiedadesWompiPublicas}) — este vive en bootstrap porque solo lo usa {@code WompiClient}, al
 * armarse aquí.
 */
@ConfigurationProperties(prefix = "tecnosport.wompi")
public record PropiedadesWompi(String secretoIntegridad) {

  public PropiedadesWompi {
    if (secretoIntegridad == null || secretoIntegridad.isBlank()) {
      throw new IllegalStateException("tecnosport.wompi.secreto-integridad no puede estar vacío.");
    }
  }
}
