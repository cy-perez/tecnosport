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
@EnableConfigurationProperties({
  PropiedadesLimiteAuth.class,
  PropiedadesLimitePedidos.class,
  PropiedadesLimiteCotizacion.class
})
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
        "/api/v1/auth/sesion",
        "/api/v1/auth/registro",
        "/api/v1/auth/verificacion",
        // Explícita, y no cubierta por la de arriba: un patrón de ruta exacto no cubre subrutas.
        // Es el mismo descuido que dejó `POST /pedidos/metodos-de-pago-disponibles` sin límite
        // cotizando contra Skydropx, que levantó la revisión adversarial de los 122 commits.
        "/api/v1/auth/verificacion/reenviar",
        "/api/v1/auth/recuperacion",
        "/api/v1/auth/recuperacion/confirmar");
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

  /**
   * La cotización tiene su propio perfil porque su riesgo es distinto: no crea nada, pero cada
   * llamada gasta cuota de un proveedor externo que se paga y que admite dos peticiones por
   * segundo. El resto de endpoints públicos solo cuestan base de datos propia.
   *
   * <p><b>Son dos rutas, no una</b>, y la segunda faltaba: {@code
   * /api/v1/pedidos/metodos-de-pago-disponibles} cotiza igual —con recaudo, así que crea una
   * cotización y la sondea— y quedaba fuera de los tres filtros. El patrón {@code /api/v1/pedidos}
   * de arriba es exacto y no cubre subrutas. Agotar las dos peticiones por segundo de la cuenta
   * deja a los compradores reales con un checkout que solo ofrece recogida en el punto y sin
   * contraentrega: la venta con envío se pierde entera mientras dure. Lo levantó una revisión
   * adversarial.
   */
  @Bean
  public FilterRegistrationBean<FiltroLimiteIntentos> filtroLimiteIntentosCotizacion(
      LimitadorDeIntentos limitadorDeIntentos,
      Reloj reloj,
      PropiedadesLimiteCotizacion propiedades) {
    FilterRegistrationBean<FiltroLimiteIntentos> registro =
        new FilterRegistrationBean<>(
            new FiltroLimiteIntentos(
                limitadorDeIntentos,
                reloj,
                propiedades.ipMaximo(),
                Duration.ofMinutes(propiedades.ipMinutos())));
    registro.addUrlPatterns(
        "/api/v1/envios/cotizacion", "/api/v1/pedidos/metodos-de-pago-disponibles");
    return registro;
  }
}
