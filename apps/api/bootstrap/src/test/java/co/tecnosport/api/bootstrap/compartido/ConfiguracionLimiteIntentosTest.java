package co.tecnosport.api.bootstrap.compartido;

import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.presentation.compartido.FiltroLimiteIntentos;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * La misma clase de prueba que {@code ConfiguracionIdempotenciaTest} y por el mismo motivo: una
 * lista de cadenas que no se nota cuando está incompleta. El endpoint responde igual, nadie ve un
 * error, y lo único distinto es que ya no hay techo.
 *
 * <p>Este archivo existe porque la omisión ya ocurrió dos veces en este repositorio: con {@code
 * /pedidos/metodos-de-pago-disponibles} —que cotizaba contra un proveedor pago sin límite— y con el
 * intento de Sistecrédito, que es peor: es público, las credenciales son productivas y cada llamada
 * abre una solicitud de crédito a nombre del documento que traiga el cuerpo ({@code adr/0048}).
 */
class ConfiguracionLimiteIntentosTest {

  private final ConfiguracionLimiteIntentos configuracion = new ConfiguracionLimiteIntentos();

  private Set<String> rutasConTecho() {
    Set<String> rutas = new HashSet<>();
    rutas.addAll(
        rutas(
            configuracion.filtroLimiteIntentosAuth(
                limitador(), reloj(), new PropiedadesLimiteAuth(5, 15, 5, 60))));
    rutas.addAll(
        rutas(
            configuracion.filtroLimiteIntentosPedidos(
                limitador(), reloj(), new PropiedadesLimitePedidos(10, 60, 10, 60))));
    rutas.addAll(
        rutas(
            configuracion.filtroLimiteIntentosCotizacion(
                limitador(), reloj(), new PropiedadesLimiteCotizacion(60, 30))));
    return rutas;
  }

  private static Collection<String> rutas(FilterRegistrationBean<FiltroLimiteIntentos> registro) {
    return registro.getUrlPatterns();
  }

  /**
   * Lo que protege: cada petición a esta ruta le pide a Sistecrédito una solicitud de crédito a
   * nombre de una persona real, con su token por SMS. El {@code Idempotency-Key} no lo impide — lo
   * elige el cliente.
   */
  @Test
  void elIntentoDeSistecreditoTieneTecho() {
    assertTrue(
        rutasConTecho().contains("/api/v1/pagos/sistecredito/intentos"),
        "el endpoint que abre solicitudes de crédito no puede quedar sin límite por IP");
  }

  @Test
  void lasRutasQueYaLoTenianLoSiguenTeniendo() {
    Set<String> rutas = rutasConTecho();

    assertTrue(rutas.contains("/api/v1/pedidos"));
    assertTrue(rutas.contains("/api/v1/pedidos/metodos-de-pago-disponibles"));
    assertTrue(rutas.contains("/api/v1/envios/cotizacion"));
    assertTrue(rutas.contains("/api/v1/auth/sesion"));
  }

  private LimitadorDeIntentos limitador() {
    return (llave, maximo, ventana, ahora) -> true;
  }

  private co.tecnosport.api.application.compartido.Reloj reloj() {
    return Instant::now;
  }
}
