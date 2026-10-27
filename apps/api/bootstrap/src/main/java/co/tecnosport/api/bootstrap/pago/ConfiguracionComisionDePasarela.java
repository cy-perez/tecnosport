package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La tarifa de Wompi como bean de dominio (ADR-0095). Aparte de {@code ConfiguracionWompi} porque
 * no la usa la pasarela sino el catálogo —el alta de variantes, el cambio de precios y el
 * recálculo—, y porque no depende de los secretos de Wompi: un arranque sin ellos sigue sabiendo
 * calcular un precio publicado.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesComisionWompi.class)
public class ConfiguracionComisionDePasarela {

  @Bean
  public ComisionDePasarela comisionDePasarela(PropiedadesComisionWompi propiedades) {
    return propiedades.comoComision();
  }
}
