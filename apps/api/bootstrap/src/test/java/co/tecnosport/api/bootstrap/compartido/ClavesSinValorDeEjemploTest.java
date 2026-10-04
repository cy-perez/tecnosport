package co.tecnosport.api.bootstrap.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.bootstrap.pago.PropiedadesWompi;
import co.tecnosport.api.bootstrap.usuario.PropiedadesAdminSemilla;
import co.tecnosport.api.bootstrap.usuario.PropiedadesJwt;
import co.tecnosport.api.presentation.envio.PropiedadesWebhookEnvio;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Los secretos que permiten <b>hacerse pasar</b> por alguien —firmar un JWT de administrador,
 * inventar un evento de Wompi o de Skydropx, entrar con la clave del primer admin— no tienen valor
 * por omisión en {@code application.yml}. Lo tienen solo en {@code application-local.yml}, que no
 * se carga en ningún despliegue.
 *
 * <p>Hasta el 4 de octubre de 2026 los cinco tenían su marcador en el archivo común, y los records
 * de propiedades solo rechazaban el vacío. Un ambiente donde faltara una variable —el primer {@code
 * apply} con {@code secretos_cargados = false}, o uno nuevo donde alguien olvide mapearla—
 * arrancaba sin quejarse con un secreto publicado en el repositorio. El marcador de Skydropx decía
 * "con él toda firma se rechaza", y era al revés: con él pasa toda firma hecha con ese mismo
 * marcador.
 *
 * <p>Se prueba contra los archivos de verdad, igual que {@code MetodosDeWompiEnApplicationYmlTest}:
 * un literal de Java en la prueba no diría nada de lo que arranca. (El nombre evita la palabra
 * inglesa a propósito: la configuración de permisos del repositorio bloquea los archivos que la
 * llevan.)
 */
class ClavesSinValorDeEjemploTest {

  private static final Map<String, String> SECRETOS =
      Map.of(
          "tecnosport.wompi.secreto-integridad", "WOMPI_SECRETO_INTEGRIDAD",
          "tecnosport.wompi.secreto-eventos", "WOMPI_SECRETO_EVENTOS",
          "tecnosport.skydropx.webhook.secreto", "SKYDROPX_SECRETO_WEBHOOK",
          "tecnosport.jwt.secreto", "JWT_SECRETO",
          "tecnosport.admin-semilla.clave", "ADMIN_CLAVE");

  private static PropertySource<?> cargar(String archivo) throws IOException {
    List<PropertySource<?>> fuentes =
        new YamlPropertySourceLoader().load(archivo, new ClassPathResource(archivo));
    return fuentes.get(0);
  }

  /** Un entorno con solo el archivo común y ninguna variable: lo que ve un despliegue olvidado. */
  private static Binder sinVariables() throws IOException {
    StandardEnvironment entorno = new StandardEnvironment();
    entorno
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    entorno.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    entorno.getPropertySources().addFirst(cargar("application.yml"));
    return Binder.get(entorno);
  }

  @Test
  void elArchivoComunNoTraeValorPorOmisionParaNingunSecreto() throws IOException {
    PropertySource<?> comun = cargar("application.yml");
    SECRETOS.forEach(
        (propiedad, variable) ->
            assertEquals(
                "${" + variable + "}",
                String.valueOf(comun.getProperty(propiedad)),
                propiedad
                    + " volvió a tener un valor por omisión en application.yml. Va en"
                    + " application-local.yml: en el común, un despliegue que olvide la variable"
                    + " arranca con un secreto publicado en el repositorio."));
  }

  @Test
  void elPerfilLocalLosTraeTodosParaQueBootRunSigaArrancandoSinEnv() throws IOException {
    PropertySource<?> local = cargar("application-local.yml");
    SECRETOS.keySet().forEach(propiedad -> assertNotNull(local.getProperty(propiedad), propiedad));
  }

  @Test
  void sinLaVariableNoArrancaNingunoDeLosCinco() throws IOException {
    Binder binder = sinVariables();

    assertThrows(Exception.class, () -> binder.bind("tecnosport.jwt", PropiedadesJwt.class));
    assertThrows(Exception.class, () -> binder.bind("tecnosport.wompi", PropiedadesWompi.class));
    assertThrows(
        Exception.class,
        () -> binder.bind("tecnosport.admin-semilla", PropiedadesAdminSemilla.class));
    assertThrows(
        Exception.class,
        () -> binder.bind("tecnosport.skydropx.webhook", PropiedadesWebhookEnvio.class));
  }

  /**
   * El Binder de Boot no falla ante un marcador que no puede resolver: lo entrega tal cual, como
   * texto. Sin esta guarda, {@code ${JWT_SECRETO}} sería una clave HS256 perfectamente válida —y
   * conocida por cualquiera que lea el YAML.
   */
  @Test
  void unMarcadorSinResolverNoPasaPorUnSecreto() {
    IllegalStateException fallo =
        assertThrows(
            IllegalStateException.class, () -> new PropiedadesJwt("${JWT_SECRETO}", 15, 30));
    assertTrue(fallo.getMessage().contains("JWT_SECRETO"), fallo.getMessage());
  }
}
