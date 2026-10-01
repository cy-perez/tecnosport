package co.tecnosport.api.bootstrap.catalogo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Del 23 de septiembre al 1 de octubre de 2026 el ambiente dev subió las imágenes a su bucket y
 * guardó en la base URLs del bucket de local: Terraform fijaba {@code GCS_BUCKET_IMAGENES} y no
 * {@code GCS_URL_PUBLICA}, y el valor por omisión de la segunda era un literal aparte. Estas
 * pruebas leen el {@code application.yml} real, no una copia, porque el defecto estaba ahí.
 */
class PropiedadesGcsTest {

  @Test
  void conSoloElBucketLaUrlPublicaApuntaAEseBucket() throws IOException {
    PropiedadesGcs propiedades =
        desdeApplicationYml(Map.of("GCS_BUCKET_IMAGENES", "tecnosport-dev-imagenes"));

    assertEquals(
        "https://storage.googleapis.com/tecnosport-dev-imagenes", propiedades.urlPublica());
  }

  @Test
  void sinVariablesTodoApuntaALocal() throws IOException {
    PropiedadesGcs propiedades = desdeApplicationYml(Map.of());

    assertEquals("tecnosport-local-imagenes", propiedades.bucketImagenes());
    assertEquals(
        "https://storage.googleapis.com/tecnosport-local-imagenes", propiedades.urlPublica());
  }

  @Test
  void unaUrlDeOtroBucketNoArranca() throws IOException {
    BindException error =
        assertThrows(
            BindException.class,
            () ->
                desdeApplicationYml(
                    Map.of(
                        "GCS_BUCKET_IMAGENES",
                        "tecnosport-dev-imagenes",
                        "GCS_URL_PUBLICA",
                        "https://storage.googleapis.com/tecnosport-local-imagenes")));
    // El binder la envuelve —BindException, luego BeanInstantiationException— y la profundidad es
    // suya, no nuestra: se busca la nuestra en la cadena.
    Throwable causa = error;
    while (causa != null && !(causa instanceof IllegalStateException)) {
      causa = causa.getCause();
    }
    assertInstanceOf(IllegalStateException.class, causa);
    assertTrue(causa.getMessage().contains("tecnosport-dev-imagenes"));
  }

  /** Un prefijo no basta: {@code tecnosport-dev-imagenes-viejo} empieza igual y es otro bucket. */
  @Test
  void unBucketQueSoloCompartePrefijoNoArranca() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new PropiedadesGcs(
                "tecnosport-dev-imagenes",
                "https://storage.googleapis.com/tecnosport-dev-imagenes-viejo",
                15));
  }

  /** Con un CDN delante el origen es otro y no hay bucket en la URL que comparar. */
  @Test
  void unaUrlFueraDeStorageNoSeCompara() {
    assertDoesNotThrow(
        () -> new PropiedadesGcs("tecnosport-dev-imagenes", "https://cdn.tecnosport.co", 15));
  }

  /**
   * Sin el entorno del sistema: un {@code GCS_URL_PUBLICA} exportado en la terminal de quien corre
   * la prueba la haría pasar o fallar por su máquina y no por el código.
   */
  private static PropiedadesGcs desdeApplicationYml(Map<String, Object> variables)
      throws IOException {
    StandardEnvironment entorno = new StandardEnvironment();
    entorno
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    entorno.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    entorno.getPropertySources().addFirst(new MapPropertySource("variables", variables));
    new YamlPropertySourceLoader()
        .load("application.yml", new ClassPathResource("application.yml"))
        .forEach(entorno.getPropertySources()::addLast);
    return Binder.get(entorno).bindOrCreate("tecnosport.gcs", PropiedadesGcs.class);
  }
}
