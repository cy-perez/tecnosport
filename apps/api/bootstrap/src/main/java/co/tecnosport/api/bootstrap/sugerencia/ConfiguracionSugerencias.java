package co.tecnosport.api.bootstrap.sugerencia;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.application.sugerencia.EnviarSugerencia;
import co.tecnosport.api.application.sugerencia.RepositorioSugerencias;
import co.tecnosport.api.bootstrap.compartido.PropiedadesLimiteSugerencias;
import co.tecnosport.api.bootstrap.legal.PropiedadesLegal;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El caso de uso del buzón se arma aquí, como todos: {@code application} no conoce Spring, así que
 * quien resuelve sus dependencias es {@code bootstrap}.
 *
 * <p>El destinatario se valida al arrancar y no al primer envío: un correo mal escrito en la
 * configuración tiene que tumbar el despliegue, no descubrirse cuando la primera sugerencia se
 * guarde y nadie se entere.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesSugerencias.class)
public class ConfiguracionSugerencias {

  @Bean
  public EnviarSugerencia enviarSugerencia(
      RepositorioSugerencias repositorioSugerencias,
      RepositorioAutorizaciones repositorioAutorizaciones,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      LimitadorDeIntentos limitadorDeIntentos,
      PropiedadesLimiteSugerencias limite,
      PropiedadesLegal legal,
      PropiedadesSugerencias propiedades) {
    return new EnviarSugerencia(
        repositorioSugerencias,
        repositorioAutorizaciones,
        enviadorDeCorreo,
        textos,
        reloj,
        limitadorDeIntentos,
        limite.cuentaMaximo(),
        Duration.ofMinutes(limite.cuentaMinutos()),
        legal.politicaDatosVersion(),
        new CorreoElectronico(propiedades.destinatario()));
  }
}
