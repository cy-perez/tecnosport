package co.tecnosport.api.bootstrap.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * La dirección de despacho lleva un {@code #} en la nomenclatura ("Cra. 26C # 38B-31"), y en YAML
 * un {@code #} precedido de espacio abre un comentario. Sin comillas, el valor por omisión se corta
 * en "Cra. 26C" y <strong>nada falla</strong>: la aplicación arranca, el cotizador manda media
 * dirección y la transportadora entrega donde puede.
 *
 * <p>Ninguna otra prueba lo atraparía, porque todas construyen {@code OrigenDespacho} a mano con un
 * literal de Java. Esta lee el {@code application.yml} de verdad.
 */
class OrigenEnApplicationYmlTest {

  private PropertySource<?> applicationYml() throws IOException {
    List<PropertySource<?>> fuentes =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
    return fuentes.get(0);
  }

  @Test
  void laDireccionDeOrigenNoSeCortaEnElNumeral() throws IOException {
    String direccion = String.valueOf(applicationYml().getProperty("tecnosport.origen.direccion"));

    assertTrue(
        direccion.contains("38B-31"),
        "El # de la nomenclatura abrió un comentario de YAML y truncó la dirección: " + direccion);
    assertTrue(direccion.contains("La Milagrosa"), direccion);
  }

  /**
   * El mismo celular que publican el pie del sitio y los textos legales. {@code npm run
   * datos-negocio} vigila las copias del frontend, pero no mira el YAML del backend.
   */
  @Test
  void elTelefonoDeOrigenEsElDelNegocio() throws IOException {
    String telefono = String.valueOf(applicationYml().getProperty("tecnosport.origen.telefono"));

    assertTrue(telefono.contains("+573138816711"), telefono);
  }

  @Test
  void laCiudadDeOrigenEsMedellin() throws IOException {
    String ciudad = String.valueOf(applicationYml().getProperty("tecnosport.origen.ciudad-dane"));

    assertTrue(ciudad.contains("05001"), ciudad);
  }

  /**
   * Las siete propiedades existen con marcador de variable de entorno. Si alguien borra una, la
   * aplicación deja de arrancar en producción y no aquí — mejor que falle aquí.
   */
  @Test
  void lasSietePropiedadesDeOrigenEstanDeclaradas() throws IOException {
    PropertySource<?> yml = applicationYml();

    for (String propiedad :
        List.of(
            "nombre",
            "telefono",
            "direccion",
            "departamento",
            "ciudad",
            "ciudad-dane",
            "codigo-postal")) {
      assertTrue(
          yml.containsProperty("tecnosport.origen." + propiedad),
          "Falta tecnosport.origen." + propiedad);
    }
  }

  /**
   * Skydropx exige los nombres del departamento y la ciudad aparte del código DANE: sin ellos la
   * cotización responde 422 y el checkout se queda sin tarifas para siempre, sin que nada falle al
   * arrancar. Verificado contra el sandbox el 11 de septiembre de 2026.
   */
  @Test
  void elDepartamentoYLaCiudadDeOrigenSonLosDeMedellin() throws IOException {
    PropertySource<?> yml = applicationYml();

    assertTrue(
        String.valueOf(yml.getProperty("tecnosport.origen.departamento")).contains("Antioquia"),
        String.valueOf(yml.getProperty("tecnosport.origen.departamento")));
    assertTrue(
        String.valueOf(yml.getProperty("tecnosport.origen.ciudad")).contains("Medellín"),
        String.valueOf(yml.getProperty("tecnosport.origen.ciudad")));
  }

  @Test
  void lasPropiedadesDeSkydropxEstanDeclaradas() throws IOException {
    PropertySource<?> yml = applicationYml();

    for (String propiedad :
        List.of(
            "url-base",
            "client-id",
            "client-secret",
            "cotizacion-timeout-segundos",
            "cotizacion-intentos")) {
      assertTrue(
          yml.containsProperty("tecnosport.skydropx." + propiedad),
          "Falta tecnosport.skydropx." + propiedad);
    }
  }

  /**
   * El retraso del sondeo tiene que caber en el tope, o el tope no sirve de nada. Con 8 intentos y
   * medio segundo entre ellos hacen falta ~4 segundos, y el tope son 10.
   */
  @Test
  void losIntentosDeSondeoCabenEnElTope() throws IOException {
    PropertySource<?> yml = applicationYml();
    int intentos =
        Integer.parseInt(
            valorPorOmision(yml.getProperty("tecnosport.skydropx.cotizacion-intentos")));
    int topeSegundos =
        Integer.parseInt(
            valorPorOmision(yml.getProperty("tecnosport.skydropx.cotizacion-timeout-segundos")));

    assertEquals(8, intentos);
    assertTrue(
        intentos * 0.5 < topeSegundos,
        "Los " + intentos + " intentos no caben en " + topeSegundos + " segundos.");
  }

  /** Extrae el valor por omisión de un marcador {@code ${VAR:valor}}. */
  private static String valorPorOmision(Object propiedad) {
    String texto = String.valueOf(propiedad);
    int separador = texto.indexOf(':');
    return texto.substring(separador + 1, texto.length() - 1);
  }
}
