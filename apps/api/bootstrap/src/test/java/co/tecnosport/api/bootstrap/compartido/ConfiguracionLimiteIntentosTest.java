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
  void elFiltroDeAuthCubreLasSeisRutasSensibles() {
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
            // La sexta, y la razón de que esta prueba exista: un patrón exacto NO cubre subrutas,
            // así que "/api/v1/auth/verificacion" no protege a "/verificacion/reenviar". Es el
            // mismo descuido que dejó sin límite el endpoint que cotiza contra Skydropx.
            "/api/v1/auth/verificacion/reenviar",
            "/api/v1/auth/recuperacion",
            "/api/v1/auth/recuperacion/confirmar"),
        Set.copyOf(registro.getUrlPatterns()));
  }
}
