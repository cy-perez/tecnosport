package co.tecnosport.api.bootstrap.usuario;

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
    if (urlLlaves == null || urlLlaves.isBlank()) {
      throw new IllegalStateException("tecnosport.google.url-llaves no puede estar vacía.");
    }
    if (urlScript == null || urlScript.isBlank()) {
      throw new IllegalStateException("tecnosport.google.url-script no puede estar vacía.");
    }
  }
}
