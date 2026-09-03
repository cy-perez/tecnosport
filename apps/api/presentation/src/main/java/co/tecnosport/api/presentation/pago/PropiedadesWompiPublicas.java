package co.tecnosport.api.presentation.pago;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Solo lo que es seguro exponer al cliente: {@code WOMPI_LLAVE_PUBLICA} y {@code WOMPI_AMBIENTE} de
 * docs/07-infra-gcp.md. El secreto de integridad nunca llega hasta aquí — vive en {@code
 * PropiedadesWompi} de bootstrap, consumido solo por {@code WompiClient}. Vive en presentation, no
 * en bootstrap, porque {@code PagoControlador} la necesita como una dependencia normal de inyección
 * (mismo criterio que {@code MapeadorRespuestasPedido}), no como una que bootstrap ensambla a mano
 * — bootstrap solo la activa con {@code @EnableConfigurationProperties} en {@code
 * ConfiguracionWompi}.
 */
@ConfigurationProperties(prefix = "tecnosport.wompi")
public record PropiedadesWompiPublicas(String llavePublica, String ambiente) {

  public PropiedadesWompiPublicas {
    if (llavePublica == null || llavePublica.isBlank()) {
      throw new IllegalStateException("tecnosport.wompi.llave-publica no puede estar vacío.");
    }
    if (ambiente == null || ambiente.isBlank()) {
      throw new IllegalStateException("tecnosport.wompi.ambiente no puede estar vacío.");
    }
  }
}
