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
    exigir(secretoIntegridad, "tecnosport.wompi.secreto-integridad", "WOMPI_SECRETO_INTEGRIDAD");
    exigir(secretoEventos, "tecnosport.wompi.secreto-eventos", "WOMPI_SECRETO_EVENTOS");
  }

  /**
   * El Binder de Boot no falla ante un marcador que no puede resolver: lo entrega como texto. Sin
   * esto, {@code ${VARIABLE}} pasaría por un secreto válido —y conocido por quien lea el YAML.
   */
  private static void exigir(String valor, String propiedad, String variable) {
    if (valor == null || valor.isBlank() || valor.contains("${")) {
      throw new IllegalStateException(
          propiedad + " no está configurado: falta la variable de entorno " + variable + ".");
    }
  }
}
