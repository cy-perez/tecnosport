package co.tecnosport.api.presentation.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La cabecera donde Skydropx manda la firma del evento, y el secreto con el que la calcula.
 *
 * <p><strong>La cabecera es configuración por una razón que dejó de ser la original.</strong> Nació
 * variable porque su nombre no estaba confirmado; hoy sí lo está, y resulta que la plataforma
 * <em>deja elegirlo en el panel</em> —entre 3 y 25 caracteres, sin espacios— con {@code
 * Authorization} por omisión (docs/13-skydropx-capacidades.md, sección 6.1). O sea que tiene que
 * seguir siendo variable, ahora por el motivo correcto.
 *
 * <p>El valor por omisión comparte nombre con la cabecera del JWT del sitio, y eso no rompe nada:
 * {@code FiltroAutenticacionJwt} solo actúa cuando el valor empieza por {@code Bearer }, y un
 * {@code HMAC ...} le pasa de largo. Queda escrito porque son dos consumidores de la misma cabecera
 * y ninguno lo sabría al leerse por separado.
 *
 * <p><strong>El secreto se genera en el panel</strong>, en Conexiones &gt; Webhooks, y hoy no lo
 * tenemos: el valor por omisión es un marcador de desarrollo, igual que las credenciales de {@code
 * PropiedadesSkydropx}. Con el marcador puesto, todo evento se rechaza — que es lo que tiene que
 * pasar cuando no hay con qué verificar. Producción lo sobrescribe desde Secret Manager.
 */
@ConfigurationProperties(prefix = "tecnosport.skydropx.webhook")
public record PropiedadesWebhookEnvio(String cabeceraFirma, String secreto) {

  public PropiedadesWebhookEnvio {
    exigir(cabeceraFirma, "tecnosport.skydropx.webhook.cabecera-firma");
    exigir(secreto, "tecnosport.skydropx.webhook.secreto");
  }

  private static void exigir(String valor, String propiedad) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException(propiedad + " no puede estar vacío.");
    }
  }
}
