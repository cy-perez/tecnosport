package co.tecnosport.api.bootstrap.compartido;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.presentation.compartido.FiltroLimiteIntentos;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Límite de intentos por IP (docs/08-seguridad-legal.md) — el límite por cuenta lo aplican los
 * casos de uso mismos, ver {@code FiltroLimiteIntentos}. Dos instancias del mismo filtro, una por
 * perfil de riesgo: {@code /auth/**} comparte el perfil "auth" (mismo tipo de abuso), pedidos tiene
 * el suyo, más generoso. {@code FiltroLimiteIntentos} no es {@code @Component} — mismo motivo que
 * {@code FiltroIdempotencia}: si lo fuera, Spring Boot lo registraría para {@code /*}.
 */
@Configuration
@EnableConfigurationProperties({PropiedadesLimiteAuth.class, PropiedadesLimitePedidos.class})
public class ConfiguracionLimiteIntentos {

  @Bean
  public FilterRegistrationBean<FiltroLimiteIntentos> filtroLimiteIntentosAuth(
      LimitadorDeIntentos limitadorDeIntentos, Reloj reloj, PropiedadesLimiteAuth propiedades) {
    FilterRegistrationBean<FiltroLimiteIntentos> registro =
        new FilterRegistrationBean<>(
            new FiltroLimiteIntentos(
                limitadorDeIntentos,
                reloj,
                propiedades.ipMaximo(),
                Duration.ofMinutes(propiedades.ipMinutos())));
    registro.addUrlPatterns(
        "/api/v1/auth/sesion", "/api/v1/auth/registro", "/api/v1/auth/recuperacion");
    return registro;
  }

  @Bean
  public FilterRegistrationBean<FiltroLimiteIntentos> filtroLimiteIntentosPedidos(
      LimitadorDeIntentos limitadorDeIntentos, Reloj reloj, PropiedadesLimitePedidos propiedades) {
    FilterRegistrationBean<FiltroLimiteIntentos> registro =
        new FilterRegistrationBean<>(
            new FiltroLimiteIntentos(
                limitadorDeIntentos,
                reloj,
                propiedades.ipMaximo(),
                Duration.ofMinutes(propiedades.ipMinutos())));
    registro.addUrlPatterns("/api/v1/pedidos");
    return registro;
  }
}
