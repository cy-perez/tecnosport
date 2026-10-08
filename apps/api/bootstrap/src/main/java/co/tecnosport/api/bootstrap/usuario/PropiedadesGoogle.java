package co.tecnosport.api.bootstrap.usuario;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code GOOGLE_CLIENT_ID} de docs/07-infra-gcp.md (ADR-0074). Vacío apaga la puerta: el sitio no
 * pinta el botón y {@code POST /auth/google} responde 404. Las dos URL son las públicas de Google y
 * tienen valor por omisión en el {@code application.yml}; viven ahí y no en el código por la regla
 * 5.
 *
 * <p>El identificador del cliente no es un secreto —va en la página que se le entrega a
 * cualquiera—, pero sí es de un ambiente: el de producción no sirve en dev porque Google comprueba
 * el origen.
 */
@ConfigurationProperties(prefix = "tecnosport.google")
public record PropiedadesGoogle(String clienteId, String urlLlaves, String urlScript) {

  public PropiedadesGoogle {
    clienteId = clienteId == null ? "" : clienteId.trim();
    exigirDeGoogle("url-llaves", urlLlaves, "www.googleapis.com");
    exigirDeGoogle("url-script", urlScript, "accounts.google.com");
  }

  /**
   * Las dos URL deciden qué se cree: con las llaves de otro, cualquiera firma un token que pasa;
   * con el script de otro, se le sirve código ajeno a quien compra. Configurables para corregirlas
   * sin redesplegar, pero solo dentro de Google y por https (revisión de seguridad del ADR-0074).
   */
  private static void exigirDeGoogle(String nombre, String url, String host) {
    URI uri;
    try {
      uri = URI.create(url == null ? "" : url.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("tecnosport.google." + nombre + " no es una URL: " + url);
    }
    if (!"https".equals(uri.getScheme()) || !host.equals(uri.getHost())) {
      throw new IllegalStateException(
          "tecnosport.google." + nombre + " tiene que ser https://" + host + "/...: " + url);
    }
  }
}
