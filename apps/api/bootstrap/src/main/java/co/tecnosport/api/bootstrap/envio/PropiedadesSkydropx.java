package co.tecnosport.api.bootstrap.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code SKYDROPX_*} de docs/07-infra-gcp.md.
 *
 * <p>{@code urlBase} es variable a propósito y <strong>todavía no está confirmada</strong>: la
 * documentación pública muestra {@code pro.skydropx.com} y {@code sb-pro.skydropx.com}, y según la
 * fuente aparecen también {@code api-pro.skydropx.com} y {@code app.skydropx.com}. Por eso es
 * configuración y no una constante con dos ambientes como en {@code WompiClient}: ahí los dos hosts
 * están documentados y aquí no. {@code TODO: confirmar el host de la cuenta colombiana en el panel,
 * Conexiones > API.}
 *
 * <p>El tope y los intentos del sondeo son parámetros técnicos, no datos de negocio: la cotización
 * es asíncrona y hay que decidir cuánto se espera antes de darla por fallida.
 */
@ConfigurationProperties(prefix = "tecnosport.skydropx")
public record PropiedadesSkydropx(
    String urlBase,
    String clientId,
    String clientSecret,
    int cotizacionTimeoutSegundos,
    int cotizacionIntentos) {

  public PropiedadesSkydropx {
    exigir(urlBase, "tecnosport.skydropx.url-base");
    exigir(clientId, "tecnosport.skydropx.client-id");
    exigir(clientSecret, "tecnosport.skydropx.client-secret");
    if (cotizacionTimeoutSegundos <= 0) {
      throw new IllegalStateException(
          "tecnosport.skydropx.cotizacion-timeout-segundos debe ser mayor que cero.");
    }
    if (cotizacionIntentos <= 0) {
      throw new IllegalStateException(
          "tecnosport.skydropx.cotizacion-intentos debe ser mayor que cero.");
    }
  }

  private static void exigir(String valor, String propiedad) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException(propiedad + " no puede estar vacío.");
    }
  }
}
