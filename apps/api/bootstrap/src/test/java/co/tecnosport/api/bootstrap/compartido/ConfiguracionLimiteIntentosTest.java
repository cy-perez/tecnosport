package co.tecnosport.api.bootstrap.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.presentation.compartido.FiltroLimiteIntentos;
import co.tecnosport.api.presentation.usuario.AutenticacionControlador;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * {@code /auth/verificacion} y {@code /auth/recuperacion/confirmar} se quedaron fuera del filtro
 * por IP (coincidencia exacta de patrón, no cubre subrutas) — las dos rutas donde un token de un
 * solo uso es el único secreto, una de ellas fija clave nueva.
 *
 * <p><b>Y hasta el 19 de septiembre de 2026 esta clase tenía una sola prueba que no protegía de
 * eso.</b> Comparaba los patrones registrados contra un conjunto escrito a mano, así que fallaba
 * cuando alguien <i>cambiaba</i> el registro —útil, porque obliga a decidirlo a propósito— y pasaba
 * en verde cuando alguien <b>añadía un endpoint y se olvidaba del filtro</b>, que es exactamente el
 * defecto que se quería evitar. Un guardián que solo detecta lo que sí se hizo no protege del
 * olvido; es el mismo género del plugin de capas que aceptaba la configuración sin aplicarla.
 *
 * <p>Se descubrió al escribir la entrada de `docs/09` que afirmaba que esta prueba había prevenido
 * la repetición del defecto. No lo había hecho: falló porque la ruta nueva se registró, no porque
 * el endpoint nuevo existiera.
 */
class ConfiguracionLimiteIntentosTest {

  /**
   * Las rutas de {@code /auth} que a propósito <b>no</b> llevan límite por IP, con su motivo. Un
   * endpoint nuevo tiene que entrar al filtro o entrar aquí; no hay tercera opción que compile en
   * verde, y eso es el objetivo.
   *
   * <ul>
   *   <li>{@code /refresco} lo llama el frontend en cada arranque, incluido el de todo visitante
   *       anónimo, porque la cookie es {@code HttpOnly} y no puede saber si hay sesión de otro
   *       modo. Limitarlo por IP tumbaría la aplicación para una oficina entera detrás de un NAT.
   *   <li>{@code /cierre} borra la cookie de quien ya la tiene. No hay nada que adivinar ni
   *       enumerar, y limitarlo solo conseguiría que alguien no pudiera cerrar su sesión.
   * </ul>
   */
  private static final Set<String> SIN_LIMITE_A_PROPOSITO =
      Set.of("/api/v1/auth/refresco", "/api/v1/auth/cierre");

  private FilterRegistrationBean<FiltroLimiteIntentos> registroDeAuth() {
    ConfiguracionLimiteIntentos configuracion = new ConfiguracionLimiteIntentos();
    LimitadorDeIntentos limitadorDeIntentos = (clave, maximoIntentos, ventana, ahora) -> true;
    Reloj reloj = Instant::now;
    PropiedadesLimiteAuth propiedades = new PropiedadesLimiteAuth(10, 15, 5, 15);
    return configuracion.filtroLimiteIntentosAuth(limitadorDeIntentos, reloj, propiedades);
  }

  /**
   * El guardián de verdad: sale del controlador, no de una lista escrita a mano. Un
   * {@code @PostMapping} nuevo en {@code AutenticacionControlador} rompe esta prueba hasta que
   * alguien decida si lleva límite o si va a {@link #SIN_LIMITE_A_PROPOSITO} con su motivo.
   *
   * <p>Por eso lee las anotaciones y no una constante: una lista que hay que acordarse de
   * actualizar tiene el mismo problema que el código que pretende vigilar.
   */
  @Test
  void ningunaRutaDeAuthSeQuedaSinLimiteSinQueAlguienLoHayaDecidido() {
    Set<String> conLimite = Set.copyOf(registroDeAuth().getUrlPatterns());
    String base = AutenticacionControlador.class.getAnnotation(RequestMapping.class).value()[0];

    Set<String> sinDecidir = new TreeSet<>();
    for (Method metodo : AutenticacionControlador.class.getDeclaredMethods()) {
      PostMapping anotacion = metodo.getAnnotation(PostMapping.class);
      if (anotacion == null) {
        continue;
      }
      for (String sufijo : anotacion.value()) {
        String ruta = base + sufijo;
        if (!conLimite.contains(ruta) && !SIN_LIMITE_A_PROPOSITO.contains(ruta)) {
          sinDecidir.add(ruta);
        }
      }
    }

    assertTrue(
        sinDecidir.isEmpty(),
        () ->
            "Estas rutas de /auth no tienen límite por IP y tampoco están en la lista de exentas"
                + " con su motivo: "
                + sinDecidir
                + ". Un patrón de ruta exacto no cubre subrutas, así que heredar el límite del"
                + " padre no ocurre. Decide: al filtro, o a SIN_LIMITE_A_PROPOSITO diciendo por"
                + " qué.");
  }

  /**
   * Y el otro lado, que sigue valiendo: que el registro no cambie sin que alguien lo note. La
   * prueba de arriba no lo cubre — quitar una ruta del filtro y añadirla a la lista de exentas la
   * dejaría en verde.
   */
  @Test
  void elFiltroDeAuthCubreLasSeisRutasSensibles() {
    assertEquals(
        Set.of(
            "/api/v1/auth/sesion",
            "/api/v1/auth/registro",
            "/api/v1/auth/verificacion",
            // La sexta: un patrón exacto NO cubre subrutas, así que
            // "/api/v1/auth/verificacion" no protege a "/verificacion/reenviar". Es el mismo
            // descuido que dejó sin límite el endpoint que cotiza contra Skydropx.
            "/api/v1/auth/verificacion/reenviar",
            "/api/v1/auth/recuperacion",
            "/api/v1/auth/recuperacion/confirmar"),
        Set.copyOf(registroDeAuth().getUrlPatterns()));
  }

  /**
   * Las rutas exentas tienen que existir de verdad. Sin esto, una exención con un dedazo —o la de
   * un endpoint que se borró— se queda ahí para siempre tapando un hueco que ya no es un hueco, y
   * el día que alguien cree esa ruta nace sin límite y en verde.
   */
  @Test
  void lasRutasExentasSiguenExistiendo() {
    String base = AutenticacionControlador.class.getAnnotation(RequestMapping.class).value()[0];
    Set<String> declaradas =
        java.util.Arrays.stream(AutenticacionControlador.class.getDeclaredMethods())
            .map(m -> m.getAnnotation(PostMapping.class))
            .filter(java.util.Objects::nonNull)
            .flatMap(a -> java.util.Arrays.stream(a.value()))
            .map(sufijo -> base + sufijo)
            .collect(Collectors.toSet());

    assertTrue(
        declaradas.containsAll(SIN_LIMITE_A_PROPOSITO),
        () ->
            "Hay exenciones que no corresponden a ninguna ruta del controlador: "
                + SIN_LIMITE_A_PROPOSITO.stream()
                    .filter(r -> !declaradas.contains(r))
                    .collect(Collectors.toSet()));
  }
}
