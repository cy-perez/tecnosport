package co.tecnosport.api.bootstrap.compartido;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.RepositorioIdempotencia;
import co.tecnosport.api.presentation.compartido.FiltroIdempotencia;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code FiltroIdempotencia} no es {@code @Component}: si lo fuera, Spring Boot lo registraría para
 * {@code /*} automáticamente. Aquí se ata a las rutas concretas que mueven dinero o inventario
 * (docs/03-api.md) — hoy solo crear pedido; cuando exista {@code POST /api/v1/pagos/intentos} se
 * agrega a la misma lista.
 */
@Configuration
public class ConfiguracionIdempotencia {

  @Bean
  public FilterRegistrationBean<FiltroIdempotencia> filtroIdempotencia(
      RepositorioIdempotencia repositorioIdempotencia, Reloj reloj) {
    FilterRegistrationBean<FiltroIdempotencia> registro =
        new FilterRegistrationBean<>(new FiltroIdempotencia(repositorioIdempotencia, reloj));
    registro.addUrlPatterns("/api/v1/pedidos");
    return registro;
  }
}
