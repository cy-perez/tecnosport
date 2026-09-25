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
  PropiedadesLimiteCotizacion.class,
  PropiedadesLimiteSeguimiento.class
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
    registro.addUrlPatterns(
        "/api/v1/pedidos",
        // Ruta exacta, así que hay que nombrarla aparte — el mismo descuido de la cotización.
        //
        // Y aquí pesa más que en ningún otro sitio: esta ruta es pública, las credenciales de
        // Sistecrédito son PRODUCTIVAS, y cada llamada **abre una solicitud de crédito real a
        // nombre del documento que venga en el cuerpo**, con su token por SMS al teléfono de esa
        // persona. El `Idempotency-Key` no sirve de control: lo elige el cliente, así que protege
        // el doble clic honesto y no al abusador. Sin este límite, cualquiera con un pedido propio
        // barato podía disparar N solicitudes contra la cédula de cualquiera (adr/0048).
        "/api/v1/pagos/sistecredito/intentos");
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

  /**
   * El seguimiento por número legible, y es el perfil más estrecho de los cuatro: es el único
   * endpoint público cuyo identificador se puede recorrer. Los números van del {@code 000001} hacia
   * arriba dentro de cada año, así que lo único que protege el pedido es que el correo coincida —y
   * quien ya conozca el correo de una persona puede probar números hasta dar con uno suyo.
   *
   * <p>El hermano que entra por {@code id} no está aquí y no hace falta: ese id es un UUID v7, y
   * recorrerlo no es una opción. Es exactamente la misma distinción que separa los dos casos de
   * uso.
   *
   * <p>Ruta exacta, como todas las de este archivo: un patrón exacto no cubre subrutas, y ese
   * descuido ya dejó dos endpoints sin límite.
   */
  @Bean
  public FilterRegistrationBean<FiltroLimiteIntentos> filtroLimiteIntentosSeguimiento(
      LimitadorDeIntentos limitadorDeIntentos,
      Reloj reloj,
      PropiedadesLimiteSeguimiento propiedades) {
    FilterRegistrationBean<FiltroLimiteIntentos> registro =
        new FilterRegistrationBean<>(
            new FiltroLimiteIntentos(
                limitadorDeIntentos,
                reloj,
                propiedades.ipMaximo(),
                Duration.ofMinutes(propiedades.ipMinutos())));
    registro.addUrlPatterns("/api/v1/pedidos/seguimiento");
    return registro;
  }
}
