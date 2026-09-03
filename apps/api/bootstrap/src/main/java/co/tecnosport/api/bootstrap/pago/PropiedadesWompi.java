package co.tecnosport.api.bootstrap.pago;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code WOMPI_SECRETO_INTEGRIDAD} y {@code WOMPI_SECRETO_EVENTOS} de docs/07-infra-gcp.md. Solo
 * los secretos: la llave pública y el ambiente no lo son y los consume presentation directamente
 * por su cuenta ({@code PropiedadesWompiPublicas}) — este vive en bootstrap porque solo lo usa
 * {@code WompiClient}, al armarse aquí.
 */
@ConfigurationProperties(prefix = "tecnosport.wompi")
public record PropiedadesWompi(String secretoIntegridad, String secretoEventos) {

  public PropiedadesWompi {
    if (secretoIntegridad == null || secretoIntegridad.isBlank()) {
      throw new IllegalStateException("tecnosport.wompi.secreto-integridad no puede estar vacío.");
    }
    if (secretoEventos == null || secretoEventos.isBlank()) {
      throw new IllegalStateException("tecnosport.wompi.secreto-eventos no puede estar vacío.");
    }
  }
}
