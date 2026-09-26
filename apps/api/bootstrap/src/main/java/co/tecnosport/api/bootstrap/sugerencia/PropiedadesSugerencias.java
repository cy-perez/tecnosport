package co.tecnosport.api.bootstrap.sugerencia;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A qué casilla llega el aviso de que entró una sugerencia.
 *
 * <p>El mismo correo del negocio que usan los avisos de envío, y por lo mismo: no es un secreto, es
 * un dato publicado, así que lleva valor por omisión real y no un placeholder (regla dura #5, que
 * prohíbe el literal de un secreto, no el de un dato público).
 */
@ConfigurationProperties(prefix = "tecnosport.sugerencias")
public record PropiedadesSugerencias(String destinatario) {

  public PropiedadesSugerencias {
    if (destinatario == null || destinatario.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.sugerencias.destinatario no puede estar vacío: un buzón que no avisa a nadie"
              + " es un buzón que nadie lee.");
    }
  }
}
