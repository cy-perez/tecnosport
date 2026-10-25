package co.tecnosport.api.bootstrap.compartido;

import co.tecnosport.api.presentation.compartido.FiltroIpDelCliente;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra {@link FiltroIpDelCliente} para todas las rutas y antes que cualquier otro filtro: el
 * límite de intentos lee la IP que este deja resuelta, y un filtro que corriera antes vería la
 * dirección del frente de Google.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesIpDelCliente.class)
public class ConfiguracionIpDelCliente {

  @Bean
  public FilterRegistrationBean<FiltroIpDelCliente> filtroIpDelCliente(
      PropiedadesIpDelCliente propiedades) {
    FilterRegistrationBean<FiltroIpDelCliente> registro =
        new FilterRegistrationBean<>(
            new FiltroIpDelCliente(propiedades.claveProxy(), propiedades.saltosConfiables()));
    registro.addUrlPatterns("/*");
    registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registro;
  }
}
