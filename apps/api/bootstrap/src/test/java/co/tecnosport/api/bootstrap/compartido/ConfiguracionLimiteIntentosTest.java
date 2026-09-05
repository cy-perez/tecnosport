package co.tecnosport.api.bootstrap.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.presentation.compartido.FiltroLimiteIntentos;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * {@code /auth/verificacion} y {@code /auth/recuperacion/confirmar} se quedaron fuera del filtro
 * por IP (coincidencia exacta de patrón, no cubre subrutas) — las dos rutas donde un token de un
 * solo uso es el único secreto, una de ellas fija clave nueva. Esta prueba falla si alguna de las
 * cinco rutas de {@code /auth} vuelve a quedar sin el filtro.
 */
class ConfiguracionLimiteIntentosTest {

  @Test
  void elFiltroDeAuthCubreLasCincoRutasSensibles() {
    ConfiguracionLimiteIntentos configuracion = new ConfiguracionLimiteIntentos();
    LimitadorDeIntentos limitadorDeIntentos = (clave, maximoIntentos, ventana, ahora) -> true;
    Reloj reloj = Instant::now;
    PropiedadesLimiteAuth propiedades = new PropiedadesLimiteAuth(10, 15, 5, 15);

    FilterRegistrationBean<FiltroLimiteIntentos> registro =
        configuracion.filtroLimiteIntentosAuth(limitadorDeIntentos, reloj, propiedades);

    assertEquals(
        Set.of(
            "/api/v1/auth/sesion",
            "/api/v1/auth/registro",
            "/api/v1/auth/verificacion",
            "/api/v1/auth/recuperacion",
            "/api/v1/auth/recuperacion/confirmar"),
        Set.copyOf(registro.getUrlPatterns()));
  }
}
