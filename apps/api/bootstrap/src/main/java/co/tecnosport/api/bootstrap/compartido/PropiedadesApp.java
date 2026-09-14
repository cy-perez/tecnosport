package co.tecnosport.api.bootstrap.compartido;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code APP_URL_PUBLICA} de docs/07-infra-gcp.md: la raíz del sitio, con la que el backend arma
 * los enlaces que manda por correo.
 *
 * <p>Vivía colgada de {@code tecnosport.verificacion-correo}, donde nació, y ya se la pedían
 * prestada dos casos de uso que no verifican ningún correo — la recuperación de clave la leía de
 * ahí, con una nota en {@code PropiedadesRecuperacionClave} explicando el préstamo. Un dato que
 * tres funcionalidades distintas necesitan no es de ninguna de las tres. La variable de entorno
 * <b>no cambia de nombre</b>: ningún despliegue tiene que enterarse de este movimiento.
 */
@ConfigurationProperties(prefix = "tecnosport.app")
public record PropiedadesApp(String urlPublica) {

  public PropiedadesApp {
    if (urlPublica == null || urlPublica.isBlank()) {
      throw new IllegalStateException("tecnosport.app.url-publica no puede estar vacío.");
    }
  }
}
