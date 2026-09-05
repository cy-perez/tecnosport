package co.tecnosport.api.bootstrap.usuario;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VERIFICACION_CORREO_HORAS_VENCIMIENTO y APP_URL_PUBLICA de docs/07-infra-gcp.md. {@code
 * urlPublica} es la primera vez que el backend la usa — hasta ahora solo la consumía el frontend —
 * para armar el enlace de verificación que va en el correo.
 */
@ConfigurationProperties(prefix = "tecnosport.verificacion-correo")
public record PropiedadesVerificacionCorreo(int horasVencimiento, String urlPublica) {

  public PropiedadesVerificacionCorreo {
    if (horasVencimiento <= 0) {
      throw new IllegalStateException(
          "tecnosport.verificacion-correo.horas-vencimiento debe ser mayor que cero.");
    }
    if (urlPublica == null || urlPublica.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.verificacion-correo.url-publica no puede estar vacío.");
    }
  }
}
