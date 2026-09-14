package co.tecnosport.api.bootstrap.usuario;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VERIFICACION_CORREO_HORAS_VENCIMIENTO de docs/07-infra-gcp.md.
 *
 * <p>Tuvo también {@code urlPublica}, que es donde APP_URL_PUBLICA entró al backend por primera
 * vez. Se fue a {@code co.tecnosport.api.bootstrap.compartido.PropiedadesApp} el día que un tercer
 * caso de uso la necesitó: la recuperación de clave ya se la pedía prestada desde aquí, y el
 * despacho habría sido el segundo préstamo.
 */
@ConfigurationProperties(prefix = "tecnosport.verificacion-correo")
public record PropiedadesVerificacionCorreo(int horasVencimiento) {

  public PropiedadesVerificacionCorreo {
    if (horasVencimiento <= 0) {
      throw new IllegalStateException(
          "tecnosport.verificacion-correo.horas-vencimiento debe ser mayor que cero.");
    }
  }
}
