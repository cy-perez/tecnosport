package co.tecnosport.api.presentation.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El nombre de la cabecera donde Skydropx manda la firma del webhook.
 *
 * <p>Es configuración y no una constante por el mismo motivo que {@code SKYDROPX_URL_BASE}:
 * <strong>no está confirmado</strong>. La única pista es de una fuente no oficial —{@code
 * authorization}, con el formato {@code HMAC {firma}}— y no se ha podido comprobar contra un evento
 * real porque la cuenta de sandbox no tiene créditos (docs/13-skydropx-capacidades.md, sección 6).
 * Si resulta ser otra, se cambia una variable de entorno y no se recompila nada.
 *
 * <p>El valor por omisión comparte nombre con la cabecera del JWT del sitio, y eso no rompe nada:
 * {@code FiltroAutenticacionJwt} solo actúa cuando el valor empieza por {@code Bearer }, y un
 * {@code HMAC ...} le pasa de largo. Queda escrito porque son dos consumidores de la misma cabecera
 * y ninguno lo sabría al leerse por separado.
 */
@ConfigurationProperties(prefix = "tecnosport.skydropx.webhook")
public record PropiedadesWebhookEnvio(String cabeceraFirma) {

  public PropiedadesWebhookEnvio {
    if (cabeceraFirma == null || cabeceraFirma.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.skydropx.webhook.cabecera-firma no puede estar vacío.");
    }
  }
}
